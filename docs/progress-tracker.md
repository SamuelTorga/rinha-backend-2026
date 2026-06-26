# Progress Tracker — Rinha de Backend 2026

Acompanhamento do desenvolvimento da solução de detecção de fraude via KNN vetorial.

---

## Status geral

| Camada | Status |
|---|---|
| DTOs e serialização JSON | ✅ Concluído |
| Carregamento do dataset | ✅ Concluído |
| Endpoint `/ready` | ✅ Concluído |
| Vetorização (14 dims) | ✅ Concluído |
| KNN brute-force | ✅ Concluído |
| Endpoint `/fraud-score` | ✅ Concluído |
| Testes unitários | ✅ Concluído |
| ExceptionMapper (payload inválido → 400) | ✅ Concluído |
| Otimização SIMD (Panama Vector API) | ⬜ Pendente |
| Docker Compose + Nginx | ⬜ Pendente |
| Dockerfile de produção | ⬜ Pendente |
| Validação end-to-end no Docker | ⬜ Pendente |
| Native Image build | ⬜ Pendente |
| Tuning de memória / JVM flags | ⬜ Pendente |

---

## Concluído

### DTOs e serialização JSON
- `TransactionRequest` — record com nested records, mapeado com `SNAKE_CASE` global + `@JsonProperty` explícito em campos com número imediato após letra (`tx_count_24h`, `max_tx_count_24h`) e boolean `is_online` (Jackson strip `is` prefix)
- `FraudScoreResponse` — serializa `fraud_score` corretamente via `SNAKE_CASE`
- `NormalizationConstants` — record, desserializa `normalization.json`
- Todos cobertos por testes unitários (JUnit 5 + AssertJ, sem `@QuarkusTest`)

### Carregamento do dataset
- `DatasetLoader` (`@Startup @ApplicationScoped`) carrega os 3 arquivos de `src/main/resources/data/` em virtual thread assíncrona no `@PostConstruct`
- Parse streaming do `references.json.gz` via Jackson sem materializar objetos intermediários
- Armazenamento em `float[]` flat contíguo: vetor `i` em `vectors[i*14 .. i*14+13]`
- `volatile boolean ready` como fence de publicação JMM — garante visibilidade de todos os campos sem volatile adicional
- Dataset real: **1.000.000 vetores** (carregamento em ~2,1 s)

### Endpoints
- `GET /ready` — 200 quando dataset carregado, 503 caso contrário
- `POST /fraud-score` — 503 se não pronto; delega para `FraudDetectionService`
- Todos com `@RunOnVirtualThread`

### Vetorização e KNN
- `FraudDetectionService.vectorize()` — implementa as 14 dimensões conforme `DETECTION_RULES.md`; sentinel `-1f` nos índices 5 e 6 quando `last_transaction == null`
- `FraudDetectionService.knn()` — brute-force k=5, distância euclidiana **ao quadrado** (sem `sqrt`), slot worst-of-5 substituído por candidato mais próximo
- Validado nos dois exemplos canônicos do spec:
  - Transação legítima → `{"approved":true,"fraud_score":0.0}` ✅
  - Transação fraude → `{"approved":false,"fraud_score":1.0}` ✅

### ExceptionMapper
- `GlobalExceptionMapper.JsonMapper` (`@Provider ExceptionMapper<JsonProcessingException>`) → 400 para JSON malformado ou campos obrigatórios ausentes
- `FallbackMapper` descartado: converter erros internos do servidor em 400 seria semânticamente incorreto e mascararia bugs reais sem melhorar o placar da competição (qualquer não-200 para payload válido já é penalidade)
- Validado com 1 teste adicional em `FraudScoreResourceTest`

### Testes
- 22 testes, todos passando
- Puro JUnit 5 + AssertJ para DTOs, vetorização e KNN (sem startup do Quarkus)
- `@QuarkusTest` + `@InjectMock` para endpoints (DatasetLoader e FraudDetectionService mockados)
- `verifyNoInteractions(detectionService)` garante que o serviço não é chamado quando não pronto

---

## Pendente

### Otimização SIMD — Panama Vector API
- Substituir o loop escalar em `squaredDistance()` por `FloatVector` (AVX2: 8 floats/instrução)
- Ganho esperado: ~4–8× no cálculo de distância → impacto direto na latência p99
- Requer `--add-modules jdk.incubator.vector` no runtime (ou módulo estável em Java 25)
- Manter o scalar como fallback para testes unitários (sem SIMD em CI genérico)

### Docker Compose + Nginx
- `docker-compose.yml` com 2 instâncias `api1` + `api2` + nginx
- `nginx/nginx.conf` — upstream round-robin, sem buffer desnecessário, keepalive
- Limites: 1 CPU total, 350 MB RAM total
- Volumes ou COPY para os arquivos de dados (16 MB gz por instância)

### Dockerfile de produção
- Baseado em `Dockerfile.jvm` existente (ou native)
- Multi-stage: build com Maven + runtime com JRE mínimo
- Passar flags JVM de memória (`-Xms`, `-Xmx`, GC tuning)
- Expor porta 8080 (internamente); Nginx faz o bind na 9999

### Native Image
- `./mvnw package -Pnative -Dquarkus.native.container-build=true`
- Reduz footprint de ~150 MB (JVM) para ~40–60 MB — viabiliza 2 instâncias em 350 MB
- Verificar compatibilidade do Panama Vector API em native (pode precisar de reflection config)
- Testar `GET /ready` e `POST /fraud-score` na imagem nativa antes de submeter

### Tuning de memória
- Dataset: 1M × 14 floats × 4 bytes = **56 MB** por instância
- Com 2 instâncias: 112 MB de dados + overhead JVM (~80–120 MB cada em JVM mode)
- Em native: footprint ~40–60 MB → 2 instâncias cabem confortavelmente
- Definir `-Xmx` adequado por instância; monitorar RSS em `docker stats`

### Validação end-to-end no Docker
- Subir com `docker compose up --build`
- `curl http://localhost:9999/ready` → 200
- Testar os dois exemplos canônicos via Nginx (porta 9999)
- Verificar round-robin entre instâncias (logs)
- Medir latência baseline com `ab` ou `k6` antes de submeter

---

## Decisões técnicas registradas

| Decisão | Motivo |
|---|---|
| `float[]` flat em vez de `float[][]` | Cache-friendliness; prep para SIMD |
| Distância euclidiana **ao quadrado** | Evita 1M `Math.sqrt` por request sem mudar ordenação |
| Slot worst-of-5 linear em vez de heap | k=5 fixo: 5 comparações por iteração bate heap em constante e cache |
| Virtual thread em todos os endpoints | Quarkus suporte nativo; sem blocking no event loop |
| `volatile ready` como única fence JMM | Uma volatile write publica todos os campos escritos antes dela |
| SNAKE_CASE global + `@JsonProperty` pontual | Elimina boilerplate; exceções documentadas em CLAUDE.md |
| Carregamento async em `@PostConstruct` | App sobe rápido; `/ready` sinaliza quando o dataset está pronto |
