# Rinha de Backend 2026 — Fraud Detection

Solução para a [Rinha de Backend 2026](https://github.com/zanfranceschi/rinha-de-backend-2026), cujo desafio é construir uma API de detecção de fraude em transações de cartão via busca vetorial (KNN).

## Stack

- **Java 25** (LTS)
- **Quarkus** com GraalVM Native Image
- **Virtual Threads** (`@RunOnVirtualThread`)
- **Panama Vector API** para cálculo de distância euclidiana via SIMD
- **Nginx** como load balancer (round-robin)

## Como funciona

Para cada transação recebida, a API:

1. Transforma o payload em um vetor de 14 dimensões, seguindo as regras de normalização definidas em [`DETECTION_RULES.md`](https://github.com/zanfranceschi/rinha-de-backend-2026/blob/main/docs/en/DETECTION_RULES.md)
2. Busca os 5 vetores mais próximos no dataset de referência (100k entradas) via distância euclidiana
3. Calcula `fraud_score = número de fraudes entre os 5 vizinhos / 5`
4. Retorna `approved = fraud_score < 0.6`

## Endpoints

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/ready` | Health check — retorna `200` quando a API está pronta |
| `POST` | `/fraud-score` | Recebe a transação e retorna a decisão de fraude |

### Exemplo

```http
POST /fraud-score
Content-Type: application/json

{
  "id": "tx-123",
  "transaction": { "amount": 384.88, "installments": 3, "requested_at": "2026-03-11T18:45:53Z" },
  "customer":    { "avg_amount": 769.76, "tx_count_24h": 3, "known_merchants": ["MERC-003"] },
  "merchant":    { "id": "MERC-016", "mcc": "5411", "avg_amount": 298.95 },
  "terminal":    { "is_online": false, "card_present": true, "km_from_home": 13.7 },
  "last_transaction": null
}
```

```json
{
  "approved": true,
  "fraud_score": 0.0
}
```

## Arquitetura

```
                        ┌─────────────────────────────────┐
                        │         docker-compose           │
                        │                                  │
          :9999         │  ┌──────────────────────────┐   │
Client ──────────────────▶ │  Nginx (load balancer)   │   │
                        │  └──────────┬───────────────┘   │
                        │             │ round-robin        │
                        │    ┌────────┴────────┐           │
                        │    ▼                 ▼           │
                        │  ┌──────┐        ┌──────┐        │
                        │  │ api1 │        │ api2 │        │
                        │  └──────┘        └──────┘        │
                        └─────────────────────────────────┘

Limites: 1 CPU + 350 MB total (nginx + api1 + api2)
```

## Decisões técnicas

**Por que Quarkus?**
Suporte de primeira classe ao GraalVM Native Image, reduzindo o footprint de memória de cada instância para ~40-60 MB — essencial dado o limite de 350 MB total. Ecossistema maduro, boa documentação e suporte nativo a Virtual Threads via `@RunOnVirtualThread`.

**Por que Native Image?**
Com duas instâncias + Nginx dentro de 350 MB, o modo JVM convencional (~150 MB por instância) simplesmente não cabe com folga. Native Image resolve o problema de memória e ainda entrega startup quase instantâneo.

**Por que Panama Vector API (SIMD)?**
O gargalo da solução é o cálculo de distância euclidiana contra 100k vetores por requisição. Com SIMD via Panama Vector API, processamos 8 floats por instrução (AVX2), reduzindo drasticamente o número de operações por query.

**Por que Nginx?**
Consome ~5-10 MB, round-robin nativo, configuração trivial. Não há nenhuma razão técnica para usar outra coisa dentro dos constraints da Rinha.

## Estrutura do projeto

```
.
├── src/
│   └── main/
│       ├── java/
│       │   └── dev/yourhandle/rinha/
│       │       ├── FraudResource.java       # endpoints REST
│       │       ├── FraudDetector.java       # lógica de KNN
│       │       ├── Vectorizer.java          # vetorização da transação
│       │       ├── DatasetLoader.java       # carrega references.json.gz
│       │       └── model/                  # DTOs de request/response
│       └── resources/
│           └── application.properties
├── resources/
│   ├── references.json.gz                  # 100k vetores rotulados
│   ├── mcc_risk.json                       # risco por categoria de merchant
│   └── normalization.json                  # constantes de normalização
├── nginx/
│   └── nginx.conf
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

## Rodando localmente

### Pré-requisitos

- Java 25+
- GraalVM 25+ (para build nativo)
- Docker e Docker Compose

### Modo JVM (desenvolvimento)

```bash
./mvnw quarkus:dev
```

### Build nativo

```bash
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

### Subindo com Docker Compose

```bash
docker compose up --build
```

### Testando

```bash
# health check
curl http://localhost:9999/ready

# fraud score
curl -s -X POST http://localhost:9999/fraud-score \
  -H "Content-Type: application/json" \
  -d @resources/example-payload.json | jq
```

## Rodando o teste oficial (k6)

Siga as instruções do repositório original para instalar o k6 e rodar o script de teste:

```bash
k6 run test/script.js
```

## Referências

- [Rinha de Backend 2026 — Repositório oficial](https://github.com/zanfranceschi/rinha-de-backend-2026)
- [DETECTION_RULES.md](https://github.com/zanfranceschi/rinha-de-backend-2026/blob/main/docs/en/DETECTION_RULES.md)
- [EVALUATION.md](https://github.com/zanfranceschi/rinha-de-backend-2026/blob/main/docs/en/EVALUATION.md)
- [Quarkus — Getting Started with Native Image](https://quarkus.io/guides/building-native-image)
- [Panama Vector API — JEP 469](https://openjdk.org/jeps/469)
