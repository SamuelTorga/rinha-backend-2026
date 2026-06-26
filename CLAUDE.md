# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## TDD

This project follows TDD. Write the test first, make it compile (with minimal stub), run it (expect it to fail), then implement until it passes.

- **Pure unit tests** (DTOs, vectorization, KNN math): plain JUnit 5 + Jackson, no `@QuarkusTest`. Fast, no Quarkus startup.
- **Endpoint tests**: `@QuarkusTest` + `@InjectMock` (from `quarkus-junit5-mockito`) to mock `DatasetLoader` so the server starts without loading the 16 MB dataset.
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=TransactionRequestTest`
- **After every code change, run `./mvnw checkstyle:check` and fix all violations before considering the task done.** Rules live in `.codequality/checkstyle.xml`.

Concrete input/output pairs from [DETECTION_RULES.md](https://github.com/zanfranceschi/rinha-de-backend-2026/blob/main/docs/en/DETECTION_RULES.md) (the legit and fraud examples) are the canonical test cases for vectorization.

## Commands

```bash
# Dev mode (hot reload, port 9999)
./mvnw quarkus:dev

# Compile only
./mvnw compile -q

# Package JAR
./mvnw package -DskipTests

# Native build (requires GraalVM or Docker)
./mvnw package -Pnative -Dquarkus.native.container-build=true

# Run via Docker Compose
docker compose up --build

# Health check
curl http://localhost:9999/ready

# Test fraud score endpoint
curl -s -X POST http://localhost:9999/fraud-score \
  -H "Content-Type: application/json" \
  -d '{"id":"tx-1","transaction":{"amount":41.12,"installments":2,"requested_at":"2026-03-11T18:45:53Z"},"customer":{"avg_amount":82.24,"tx_count_24h":3,"known_merchants":["MERC-016"]},"merchant":{"id":"MERC-016","mcc":"5411","avg_amount":60.25},"terminal":{"is_online":false,"card_present":true,"km_from_home":29.23},"last_transaction":null}'
```

## Architecture

### Request pipeline

```
POST /fraud-score
  → FraudScoreResource       (JAX-RS, @RunOnVirtualThread)
  → FraudDetectionService    (to be created: vectorize + KNN)
  → DatasetLoader            (in-memory flat float[] of reference vectors)
```

### DatasetLoader — startup and thread safety

`DatasetLoader` (`@Startup @ApplicationScoped`) fires a virtual thread in `@PostConstruct` that loads three classpath resources from `src/main/resources/data/`:

| File | Purpose |
|---|---|
| `references.json.gz` | 3M labeled vectors (parsed via Jackson streaming API into a flat `float[]`) |
| `mcc_risk.json` | `Map<String, Float>` MCC → risk score (default 0.5 for unknown MCCs) |
| `normalization.json` | Deserialized into `NormalizationConstants` record |

The `volatile boolean ready` field acts as the **JMM publication fence**: all writes to `vectors`, `isfraud`, `mccRisk`, and `normalization` happen before `ready = true`. Readers that observe `ready == true` are guaranteed to see all those writes — do not reorder or add intermediate volatile writes.

`GET /ready` returns 503 until `ready == true`. `POST /fraud-score` also checks it and returns 503 if not ready.

### Vector storage layout

```java
float[] vectors;    // flat array: vector i starts at index i * 14
boolean[] isfraud;  // isfraud[i] == true means vectors[i*14..i*14+13] is fraud
int vectorCount;    // actual number of loaded entries
```

This layout is intentional for cache-friendliness and Panama Vector API (SIMD) distance computation.

### 14-dimension vectorization (to be implemented in `FraudDetectionService`)

Dimensions in order (see [DETECTION_RULES.md](https://github.com/zanfranceschi/rinha-de-backend-2026/blob/main/docs/en/DETECTION_RULES.md)):

| idx | field | formula |
|-----|-------|---------|
| 0 | amount | `clamp(amount / maxAmount)` |
| 1 | installments | `clamp(installments / maxInstallments)` |
| 2 | amount_vs_avg | `clamp((amount / avgAmount) / amountVsAvgRatio)` |
| 3 | hour_of_day | `hour(requestedAt) / 23` (UTC) |
| 4 | day_of_week | `dayOfWeek(requestedAt) / 6` (Mon=0, Sun=6) |
| 5 | minutes_since_last_tx | `clamp(minutes / maxMinutes)` or **`-1f`** if `lastTransaction == null` |
| 6 | km_from_last_tx | `clamp(kmFromCurrent / maxKm)` or **`-1f`** if `lastTransaction == null` |
| 7 | km_from_home | `clamp(kmFromHome / maxKm)` |
| 8 | tx_count_24h | `clamp(txCount24h / maxTxCount24h)` |
| 9 | is_online | `1f` / `0f` |
| 10 | card_present | `1f` / `0f` |
| 11 | unknown_merchant | `1f` if merchant.id NOT in knownMerchants, else `0f` |
| 12 | mcc_risk | `mccRisk.getOrDefault(mcc, 0.5f)` |
| 13 | merchant_avg_amount | `clamp(merchantAvgAmount / maxMerchantAvgAmount)` |

`clamp(x)` = `Math.max(0f, Math.min(1f, x))`. The `-1f` sentinel at indices 5 and 6 is the **only** value allowed outside `[0, 1]` — do not replace or filter it.

KNN: k=5, Euclidean distance, brute-force. `fraud_score = fraudCount / 5`. `approved = fraud_score < 0.6`.

### Jackson configuration

`quarkus.jackson.property-naming-strategy=SNAKE_CASE` is set globally — all DTO fields are **camelCase in Java** and automatically map to/from **snake_case in JSON**. Three fields require `@JsonProperty` because SNAKE_CASE alone doesn't produce the right key:

| Java field | Auto SNAKE_CASE | Actual JSON key | Fix |
|---|---|---|---|
| `Terminal.isOnline` | `"online"` (strips `is` prefix) | `"is_online"` | `@JsonProperty("is_online")` |
| `Customer.txCount24h` | `"tx_count24h"` (no `_` before digit) | `"tx_count_24h"` | `@JsonProperty("tx_count_24h")` |
| `NormalizationConstants.maxTxCount24h` | `"max_tx_count24h"` | `"max_tx_count_24h"` | `@JsonProperty("max_tx_count_24h")` |

**General rule**: any field whose name contains a number immediately after a letter requires an explicit `@JsonProperty`.

## Infrastructure constraints

- **1 CPU, 350 MB RAM total** across Nginx + api1 + api2
- Nginx: ~5-10 MB; leaves ~170 MB per JVM instance
- The `float[vectorCount * 14]` array is ~168 MB for 3M vectors — target Native Image to keep the JVM footprint small enough for two instances
- Port exposed externally: **9999** (Nginx), API instances communicate internally

## Key files not yet implemented

- `FraudDetectionService.java` — vectorization + KNN (next step)
- `docker-compose.yml` + `nginx/nginx.conf` — deployment config
