# PRD — nlp-kotlin-playground v0.1.0

> **Expansão arquitetural: do monolito síncrono para sistema distribuído com queue + storage + persistência.**
>
> Esta release é um **breaking change** do playground. Substitui o processamento síncrono in-memory por uma arquitetura realista de produção: MinIO (S3-compatible), RabbitMQ (queue), SQLite (persistência), consumers paralelos e máquina de estados.

---

## 📋 Documento de requisitos para implementação assistida via Claude Code

Este PRD descreve a expansão completa, decisões já tomadas, escopo, plano de fases, critérios de aceitação e armadilhas conhecidas. **Leia tudo antes de começar.**

**Versão alvo:** v0.1.0

---

## 1. Contexto e Motivação

### 1.1. Por que essa expansão existe

O `nlp-kotlin-playground` funcionou: prova que Tessera + Mosaic operam end-to-end. Mas tem limitações arquiteturais:

- **Processamento síncrono** no thread HTTP — usuário sobe corpus, espera 30-60s no spinner, conexão pode cair
- **Tudo em memória** — restart do container = sessões perdidas
- **Sem observabilidade** — se algo trava, ninguém sabe onde
- **Não escala** — múltiplos uploads simultâneos travam um ao outro

Essa expansão resolve isso transformando o playground numa **mini-arquitetura distribuída**, ainda containerizada num único Docker Compose, mas com componentes profissionais conectados.

### 1.2. Objetivo principal: portfolio sênior

demonstra **capacidade técnica** (Kotlin + Tessera + Mosaic + Docker). v0.1.0 demonstra **maturidade arquitetural** (queues, storage, state machine, observability). É a diferença entre "fez um demo" e "demonstrou que entende como aplicações reais são construídas".

Recrutador técnico abrindo o repo deve pensar: "esse cara entende sistemas distribuídos, não só código."

### 1.3. Princípios não-negociáveis

- **Tudo continua em Kotlin/JVM puro.** Backend, consumers, scripts auxiliares.
- **Tudo orquestrado via Docker Compose.** Nenhum serviço externo, nenhum cloud SDK. Quem clona e roda `docker-compose up` tem tudo funcionando localmente.
- **Containers efêmeros e idempotentes.** Reiniciar qualquer serviço não corrompe estado. Volumes persistem o que precisa persistir (SQLite, MinIO).
- **Tessera e Mosaic continuam como dependências JitPack.** Não reimplementar nem submodular.
- **Mesma stack de qualidade.** ktlint, detekt, kover, GitHub Actions, dependabot — tudo continua.

### 1.4. Decisões arquiteturais já tomadas (NÃO RE-DECIDIR)

1. **Storage de blob: MinIO** (S3-compatible), container separado no compose
2. **Queue: RabbitMQ** com **exchange durável + queue durável + manual acks + DLQ**
3. **Persistência de estado: SQLite** via **Exposed ORM** (idiomático Kotlin/JetBrains)
4. **Concorrência inicial: 2 consumers** (configurável via env)
5. **Status updates no frontend: polling** (HTTP a cada ~2s). SSE fica como stretch goal pós-v0.1.0
6. **Storage não persiste corpus uploadados eternamente.** Lifecycle: corpus baixado pelo consumer → treinado → deletado do MinIO. Modelos treinados (tessera.json + mosaic.bin) ficam num bucket separado, com TTL de 24h.
7. **SQLite single-file**, persistido via volume Docker. Sem backup automático (é demo).
8. **Sem autenticação.** O playground continua sendo single-tenant pra demo.
9. **Breaking change permitido.** v0.1.0 não é retrocompatível. Endpoints mudam, comportamento muda. README explica.
10. **Máquina de estados rígida.** Estados e transições definidos exatamente, validados em código.

---

## 2. Escopo

### 2.1. Dentro do escopo (MUST HAVE)

#### Infraestrutura novos serviços

- **MinIO** rodando em container, com 2 buckets pré-criados via init script:
  - `corpus-uploads`: arquivos de corpus recém-uploadados (TTL automático: 1h)
  - `trained-models`: modelos treinados (`tessera.json`, `mosaic.bin`, metadata) — TTL 24h
- **RabbitMQ** em container, com management UI exposta na porta 15672
- **SQLite** persistido em volume Docker

#### Backend Ktor — produtor

- Endpoint `POST /upload` agora:
  - Recebe corpus
  - Salva no MinIO bucket `corpus-uploads` com UUID
  - Cria registro no SQLite com status `QUEUED`
  - Publica mensagem no RabbitMQ exchange `training.exchange` com routing key `training.requested`
  - Retorna 202 Accepted com `training_id`
- Endpoint novo `GET /api/training/{id}`: retorna estado atual + timeline de transições
- Endpoint novo `GET /api/trainings`: lista paginada de trainings com filtros (status, data)
- Endpoint existente `GET /api/status/{sessionId}`: ainda funciona mas agora consulta SQLite, não memória

#### Backend Ktor — consumer

- Worker pool (default 2, configurável) escutando `training.queue`
- Cada consumer:
  1. Recebe mensagem com URL/key do MinIO
  2. Atualiza SQLite: `DOWNLOADING`
  3. Baixa blob do MinIO pra `/tmp/{uuid}.txt`
  4. Atualiza SQLite: `TOKENIZING`
  5. Roda Tessera (treina BPE)
  6. Atualiza SQLite: `EMBEDDING`
  7. Cria Mosaic embedding compatível
  8. Atualiza SQLite: `INDEXING`
  9. Pre-computa frases e mean vectors pra busca
  10. Sobe artefatos (`tessera.json`, `mosaic.bin`, metadata) pro MinIO bucket `trained-models`
  11. Atualiza SQLite: `READY` com URL dos artefatos
  12. **Deleta o arquivo `/tmp/`** (limpeza explícita)
  13. **Deleta o blob original** do bucket `corpus-uploads`
  14. Manual ack na fila
- Se qualquer etapa falhar: atualiza SQLite `FAILED` com erro, nack sem requeue → mensagem vai pra DLQ

#### Máquina de estados

```
QUEUED → DOWNLOADING → TOKENIZING → EMBEDDING → INDEXING → READY
                                                          ↘ FAILED (em qualquer etapa)
                                                          ↘ EXPIRED (TTL 24h sem uso)
```

Transições inválidas (ex: `READY → DOWNLOADING`) são bloqueadas em código com exceção.

#### Frontend

- **Mantém abas existentes** (Search, Tokenize, Compare) — funcionam normalmente após training READY
- **Nova aba "Trainings"**: dashboard listando todos os trainings com:
  - Status atual com cor (Queued=cinza, Em progresso=azul, Ready=verde, Failed=vermelho)
  - Tempo desde criação
  - Tempo na etapa atual (destaca se > 5min = potencial stuck)
  - Filtros: por status (multi-select), por data (last hour / today / all)
  - Botão "View details" expande timeline de transições
  - Auto-refresh a cada 3 segundos via polling
- Página de upload agora redireciona pra `/training/{id}/progress` que polls o status

#### Observabilidade

- Logs estruturados (JSON) com correlation ID por training
- Endpoint `/health` agora valida conexões com MinIO, RabbitMQ, SQLite
- Endpoint `/metrics` (texto simples, formato Prometheus-like) com contadores: trainings_queued_total, trainings_completed_total, trainings_failed_total

### 2.2. Fora do escopo (NÃO FAZER)

- Autenticação / contas / multi-tenancy
- Backup ou replicação de SQLite
- Kubernetes manifests, Helm charts
- Monitoramento real (Prometheus/Grafana) — só endpoint texto
- WebSockets (decisão de polling antes do SSE)
- Migrations versionadas com Flyway/Liquibase — usa `SchemaUtils.create` do Exposed
- Cancelamento de training em progresso (consumer não preempta)
- Retry automático após falha — falhas vão pra DLQ pra inspeção manual
- Upload de múltiplos arquivos / ZIP

### 2.3. Stretch goals (NICE TO HAVE, só após Done)

- **SSE** substituindo polling no dashboard
- **DLQ visualization**: aba mostrando mensagens na DLQ com botão "requeue"
- **Métricas Prometheus** completas (counters, histograms, gauges)
- **Visualização de gráfico** de trainings ao longo do tempo
- **Bulk delete** de trainings antigos
- **Dark mode**

---

## 3. Definição de "Pronto" (Done)

### 3.1. Critérios funcionais

- [ ] `docker-compose up` sobe 4 containers (app, MinIO, RabbitMQ, dependencies do app)
- [ ] App detecta MinIO + RabbitMQ + SQLite disponíveis antes de aceitar requests (`/health` retorna 200 só quando todos OK)
- [ ] Upload de corpus de 100KB completa fluxo end-to-end (upload → ready) em < 60s
- [ ] Falha proposital (corpus inválido) marca training como FAILED e mensagem vai pra DLQ
- [ ] Restart do container app **não perde** trainings em andamento (consumer re-pega mensagem da fila, SQLite preserva estado)
- [ ] Restart do RabbitMQ **não perde** mensagens (queue + exchange duráveis)
- [ ] Restart do MinIO **não perde** blobs (volume persiste)
- [ ] Restart do SQLite **não perde** histórico (volume persiste)
- [ ] 5 uploads simultâneos processam em pares de 2 (concorrência configurada)
- [ ] Dashboard mostra todos trainings com status correto e atualizado
- [ ] Filtros do dashboard funcionam (por status, por data)

### 3.2. Critérios de qualidade

- [ ] `./gradlew test` passa
- [ ] Cobertura de testes ≥ 60% (relaxado vs 80% dos outros projetos — escopo distribuído tem mais surface de teste)
- [ ] `./gradlew ktlintCheck detekt` sem warnings
- [ ] Logs estruturados em JSON
- [ ] Sem secrets hardcoded (usar env vars)

### 3.3. Critérios de Docker

- [ ] `docker-compose up` em 1 comando, sem variáveis obrigatórias
- [ ] `docker-compose down -v` limpa tudo (incluindo volumes)
- [ ] App container roda como non-root user
- [ ] Healthchecks em todos os containers
- [ ] App container espera MinIO e RabbitMQ healthy antes de subir (depends_on com condition)
- [ ] Tamanho da imagem app final < 350MB (foi 300MB, expansão justifica +50MB)

### 3.4. Critérios de portfolio

- [ ] **Vídeo demo atualizado** mostrando arquitetura nova (upload → vai pra MinIO → mensagem na fila → consumer processa → SQLite atualiza → frontend reflete)
- [ ] README com diagrama de arquitetura (componentes + setas)
- [ ] ARCHITECTURE.md atualizado explicando cada decisão (por que MinIO vs filesystem, por que SQLite vs Postgres, por que Exposed vs JDBC puro, por que polling vs SSE)
- [ ] Seção "Why this architecture?" no README explicando trade-offs

### 3.5. Critérios de versionamento

- [ ] Tag `v0.1.0` criada via release workflow
- [ ] Imagem Docker `ghcr.io/hectorifc/nlp-kotlin-playground:v0.1.0` publicada
- [ ] CHANGELOG.md detalha breaking changes
- [ ] README aponta claramente "v0.1.0 introduces queue-based architecture (breaking change)"

---

## 4. Especificação Técnica

### 4.1. Stack atualizada

| Componente | Tecnologia | Versão | Justificativa |
|------------|------------|--------|---------------|
| Backend | Ktor | 3.x | Já estava |
| Linguagem | Kotlin | 2.0+ | Continuidade |
| Persistência | SQLite | 3.x | Single-file, sem servidor, perfeito pra demo |
| ORM | Exposed | 0.55+ | Idiomático Kotlin, type-safe, mantido por JetBrains |
| Queue | RabbitMQ | 3.x management | Padrão de indústria, suporte completo a AMQP |
| Cliente queue | `com.rabbitmq:amqp-client` | 5.x | Oficial Java, funciona em Kotlin |
| Blob storage | MinIO | latest | S3-compatible, leve, popular pra dev/test |
| Cliente storage | `io.minio:minio` | 8.x | Cliente Java oficial |
| Tessera | JitPack | v0.0.7+ | Já era dependência |
| Mosaic | JitPack | v0.0.4+ | Já era dependência |
| Logging | Logback + Logstash encoder | latest | JSON structured logs |

### 4.2. Topologia Docker Compose

```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      - MINIO_ENDPOINT=http://minio:9000
      - MINIO_ACCESS_KEY=playground
      - MINIO_SECRET_KEY=playground123
      - RABBITMQ_HOST=rabbitmq
      - RABBITMQ_USER=guest
      - RABBITMQ_PASS=guest
      - SQLITE_PATH=/data/playground.db
      - CONSUMER_CONCURRENCY=2
    volumes:
      - sqlite-data:/data
    depends_on:
      minio: { condition: service_healthy }
      rabbitmq: { condition: service_healthy }
      minio-init: { condition: service_completed_successfully }

  minio:
    image: minio/minio:latest
    ports: ["9000:9000", "9001:9001"]
    environment:
      - MINIO_ROOT_USER=playground
      - MINIO_ROOT_PASSWORD=playground123
    volumes:
      - minio-data:/data
    command: server /data --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 10s

  minio-init:
    image: minio/mc:latest
    depends_on:
      minio: { condition: service_healthy }
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 playground playground123;
      mc mb local/corpus-uploads --ignore-existing;
      mc mb local/trained-models --ignore-existing;
      mc ilm rule add --expire-days 1 local/corpus-uploads;
      mc ilm rule add --expire-days 1 local/trained-models;
      "

  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s

volumes:
  sqlite-data:
  minio-data:
```

### 4.3. Schema do SQLite

```sql
-- Tabela principal de trainings
CREATE TABLE trainings (
    id TEXT PRIMARY KEY,           -- UUID
    status TEXT NOT NULL,           -- enum string
    corpus_blob_key TEXT,           -- key no bucket corpus-uploads
    corpus_size_bytes INTEGER,
    corpus_filename TEXT,
    model_blob_prefix TEXT,         -- prefix no bucket trained-models (key tessera.json, mosaic.bin)
    error_message TEXT,
    created_at INTEGER NOT NULL,    -- epoch millis
    updated_at INTEGER NOT NULL,
    expires_at INTEGER              -- epoch millis, NULL antes de READY
);

CREATE INDEX idx_trainings_status ON trainings(status);
CREATE INDEX idx_trainings_created_at ON trainings(created_at DESC);

-- Audit log de transições
CREATE TABLE training_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    training_id TEXT NOT NULL,
    from_status TEXT,               -- NULL para evento inicial
    to_status TEXT NOT NULL,
    detail TEXT,                    -- contexto livre (filename, error stack)
    occurred_at INTEGER NOT NULL,
    FOREIGN KEY (training_id) REFERENCES trainings(id)
);

CREATE INDEX idx_events_training_id ON training_events(training_id, occurred_at);
```

Exposed equivalente em Kotlin DSL fica em `src/main/kotlin/dev/nlpplayground/persistence/Schema.kt`.

### 4.4. Definição da máquina de estados

```kotlin
enum class TrainingStatus {
    QUEUED,
    DOWNLOADING,
    TOKENIZING,
    EMBEDDING,
    INDEXING,
    READY,
    FAILED,
    EXPIRED
}

internal object TrainingStateMachine {
    private val transitions: Map<TrainingStatus, Set<TrainingStatus>> = mapOf(
        TrainingStatus.QUEUED to setOf(TrainingStatus.DOWNLOADING, TrainingStatus.FAILED),
        TrainingStatus.DOWNLOADING to setOf(TrainingStatus.TOKENIZING, TrainingStatus.FAILED),
        TrainingStatus.TOKENIZING to setOf(TrainingStatus.EMBEDDING, TrainingStatus.FAILED),
        TrainingStatus.EMBEDDING to setOf(TrainingStatus.INDEXING, TrainingStatus.FAILED),
        TrainingStatus.INDEXING to setOf(TrainingStatus.READY, TrainingStatus.FAILED),
        TrainingStatus.READY to setOf(TrainingStatus.EXPIRED),
        // Estados terminais não permitem transição
        TrainingStatus.FAILED to emptySet(),
        TrainingStatus.EXPIRED to emptySet()
    )

    fun assertValidTransition(from: TrainingStatus, to: TrainingStatus) {
        val allowed = transitions[from] ?: emptySet()
        require(to in allowed) {
            "Invalid transition: $from → $to. Allowed: $allowed"
        }
    }
}
```

### 4.5. RabbitMQ topology

```kotlin
internal object QueueTopology {
    const val EXCHANGE = "training.exchange"
    const val ROUTING_KEY = "training.requested"
    const val QUEUE = "training.queue"
    
    const val DLX = "training.dlx"
    const val DLQ = "training.dlq"
    
    fun declare(channel: Channel) {
        // Exchange principal — durável
        channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.DIRECT, true)
        
        // DLX (dead letter exchange) — durável
        channel.exchangeDeclare(DLX, BuiltinExchangeType.FANOUT, true)
        
        // Queue principal — durável, com DLX configurado
        channel.queueDeclare(
            QUEUE,
            true,                                  // durable
            false,                                 // exclusive
            false,                                 // autoDelete
            mapOf(
                "x-dead-letter-exchange" to DLX,
                "x-message-ttl" to 600_000         // 10min TTL na queue
            )
        )
        channel.queueBind(QUEUE, EXCHANGE, ROUTING_KEY)
        
        // DLQ — durável
        channel.queueDeclare(DLQ, true, false, false, null)
        channel.queueBind(DLQ, DLX, "")
    }
}
```

Mensagem payload (JSON):
```json
{
  "training_id": "uuid",
  "blob_key": "corpus-uploads/uuid.txt",
  "submitted_at": 1737000000000
}
```

### 4.6. Endpoints HTTP atualizados

| Método | Path | Descrição | Mudança |
|--------|------|-----------|-------------------|
| GET | `/` | Home | Sem mudança |
| GET | `/pretrained` | Lista modelos pré-treinados | Sem mudança |
| POST | `/upload` | Upload corpus | **BREAKING**: agora retorna 202 + `training_id`, processa async |
| GET | `/training/{id}/progress` | Página HTML mostrando progresso | NOVO |
| GET | `/api/training/{id}` | Estado + timeline de um training | NOVO |
| GET | `/api/trainings` | Lista paginada com filtros | NOVO |
| GET | `/api/trainings?status=QUEUED,DOWNLOADING` | Com filtros | NOVO |
| GET | `/explore/{trainingId}` | Tela de exploração (renomeado de sessionId) | **BREAKING**: usa training_id |
| POST | `/api/search/{trainingId}` | Search | **BREAKING**: usa training_id |
| POST | `/api/tokenize/{trainingId}` | Tokenize | **BREAKING**: usa training_id |
| POST | `/api/similarity/{trainingId}` | Compare | **BREAKING**: usa training_id |
| GET | `/api/trainings/active` | Dashboard data feed | NOVO |
| GET | `/health` | Healthcheck | Atualizado: valida MinIO + RabbitMQ + SQLite |
| GET | `/metrics` | Contadores básicos | NOVO |

### 4.7. Estrutura de pacotes

```
src/main/kotlin/dev/nlpplayground/
├── Application.kt
├── Config.kt                       # carrega env vars
│
├── routes/
│   ├── HomeRoute.kt
│   ├── UploadRoute.kt              # publica na queue
│   ├── ExploreRoute.kt             # consulta SQLite pra trainingId
│   ├── TrainingRoute.kt            # GET /api/training, /api/trainings
│   ├── HealthRoute.kt              # valida dependências
│   ├── MetricsRoute.kt
│   └── ApiRoute.kt                 # search, tokenize, compare
│
├── persistence/
│   ├── Database.kt                 # init SQLite + Exposed
│   ├── Schema.kt                   # tabelas Trainings, TrainingEvents
│   ├── TrainingRepository.kt
│   └── TrainingEventRepository.kt
│
├── storage/
│   ├── BlobStorage.kt              # interface
│   ├── MinioBlobStorage.kt         # impl
│   └── BlobKey.kt                  # value class
│
├── messaging/
│   ├── QueueTopology.kt            # declare exchanges/queues/DLQ
│   ├── RabbitConnection.kt         # connection holder
│   ├── TrainingPublisher.kt        # publica mensagens
│   └── TrainingConsumer.kt         # consome mensagens, executa pipeline
│
├── training/
│   ├── TrainingStatus.kt           # enum
│   ├── TrainingStateMachine.kt     # validações de transição
│   ├── TrainingService.kt          # orquestra estado + side effects
│   └── pipeline/                   # mantido
│       ├── PretrainedLoader.kt
│       ├── CorpusTrainer.kt
│       └── SemanticSearch.kt
│
├── observability/
│   ├── CorrelationId.kt            # MDC + Ktor plugin
│   ├── MetricsRegistry.kt          # counters in-memory
│   └── LoggingConfig.kt
│
└── ui/
    └── templates/                  # Mustache templates pra novas views
```

### 4.8. Idempotência do consumer

RabbitMQ pode entregar a mesma mensagem 2x em caso de network glitch ou crash do consumer. Solução: ao receber mensagem, **antes de processar**, checar SQLite:

```kotlin
fun handle(message: TrainingMessage) {
    val current = repo.findById(message.trainingId)
    
    if (current == null) {
        log.warn("Received message for unknown training ${message.trainingId}, acking and discarding")
        return  // ack, sem processar
    }
    
    when (current.status) {
        TrainingStatus.QUEUED -> proceed(current)
        TrainingStatus.READY, TrainingStatus.FAILED, TrainingStatus.EXPIRED -> {
            log.info("Training ${message.trainingId} already in terminal state ${current.status}, skipping")
            return  // ack, sem processar
        }
        else -> {
            // Estados intermediários: provavelmente outro consumer travou, reprocessar
            log.warn("Training ${message.trainingId} in intermediate state ${current.status}, reprocessing from scratch")
            proceed(current.copy(status = TrainingStatus.QUEUED))
        }
    }
}
```

### 4.9. Cleanup de recursos

Crítico pra "não inchar contêiner":

```kotlin
fun process(message: TrainingMessage): Result<Unit> {
    val tempFile = Files.createTempFile("corpus-", ".txt")
    
    try {
        // Download
        storage.download(message.blobKey, tempFile.outputStream())
        
        // Treina (Tessera + Mosaic)
        val pipeline = trainer.train(tempFile)
        
        // Sobe modelos
        storage.upload(modelKey("tessera.json"), pipeline.tokenizer.toJson())
        storage.upload(modelKey("mosaic.bin"), pipeline.embeddings.toBytes())
        
        // Marca READY
        service.markReady(message.trainingId, modelPrefix)
        
        // Limpa blob de corpus (já foi treinado)
        storage.delete(message.blobKey)
        
        return Result.success(Unit)
    } catch (e: Exception) {
        service.markFailed(message.trainingId, e.message ?: "Unknown error")
        return Result.failure(e)
    } finally {
        Files.deleteIfExists(tempFile)  // Sempre limpa, mesmo em sucesso ou erro
    }
}
```

### 4.10. Polling do frontend

JS no `training/{id}/progress.html`:

```javascript
async function pollStatus(trainingId) {
    while (true) {
        const res = await fetch(`/api/training/${trainingId}`);
        const data = await res.json();
        
        updateUI(data);  // atualiza progress bar, lista de eventos
        
        if (['READY', 'FAILED', 'EXPIRED'].includes(data.status)) {
            if (data.status === 'READY') {
                window.location.href = `/explore/${trainingId}`;
            }
            break;
        }
        
        await sleep(2000);
    }
}
```

**Detalhe importante:** intervalo de 2-3s, não menos. Polling agressivo (500ms) sobrecarrega o servidor sem benefício real.

### 4.11. Configuração via env vars

| Variável | Default | Propósito |
|----------|---------|-----------|
| `MINIO_ENDPOINT` | `http://minio:9000` | URL do MinIO (interna ao compose) |
| `MINIO_PUBLIC_URL` | `http://localhost:9000` | URL pública (pra apresentar ao usuário, se aplicável) |
| `MINIO_ACCESS_KEY` | `playground` | Credencial |
| `MINIO_SECRET_KEY` | `playground123` | Credencial |
| `RABBITMQ_HOST` | `rabbitmq` | Host AMQP |
| `RABBITMQ_USER` | `guest` | Credencial |
| `RABBITMQ_PASS` | `guest` | Credencial |
| `SQLITE_PATH` | `/data/playground.db` | Caminho do arquivo |
| `CONSUMER_CONCURRENCY` | `2` | Workers paralelos |
| `MAX_CORPUS_SIZE_BYTES` | `2097152` (2MB) | Limite hard |
| `TRAINING_TTL_HOURS` | `24` | Tempo até EXPIRED |

---

## 5. Plano de Implementação em Fases

**Cronograma realista: 7 dias úteis com 4-6h/dia de trabalho focado.**

### Fase 0 — Setup infraestrutura (estimativa: 1 dia)

**Objetivo:** Docker Compose multi-container, todos serviços healthy, app conectando neles.

- [ ] Atualizar `docker-compose.yml` com MinIO + RabbitMQ + init script + volumes
- [ ] Adicionar dependências no `build.gradle.kts`:
  - `org.jetbrains.exposed:exposed-core`, `-dao`, `-jdbc`, `-java-time`
  - `org.xerial:sqlite-jdbc`
  - `com.rabbitmq:amqp-client`
  - `io.minio:minio`
  - `net.logstash.logback:logstash-logback-encoder`
- [ ] `Config.kt` carregando env vars com defaults
- [ ] `Database.kt` inicializando SQLite + criando schema via Exposed
- [ ] `RabbitConnection.kt` conectando ao broker
- [ ] `MinioBlobStorage.kt` conectando ao MinIO, validando buckets
- [ ] `/health` validando os 3 serviços
- [ ] `docker-compose up` sobe tudo, `/health` retorna 200

**Critério de saída:** todos containers rodando, app conecta nos 3 serviços, healthcheck verde.

### Fase 1 — Persistência e máquina de estados (estimativa: 1 dia)

**Objetivo:** SQLite operacional, state machine validada por testes.

- [ ] `Schema.kt` com tabelas `Trainings` e `TrainingEvents` via Exposed
- [ ] `TrainingStatus.kt` enum
- [ ] `TrainingStateMachine.kt` com validações + testes unitários
- [ ] `TrainingRepository.kt`: `create`, `findById`, `findAll`, `updateStatus`, `markExpired`
- [ ] `TrainingEventRepository.kt`: `record(trainingId, from, to, detail)`, `findByTrainingId`
- [ ] Testes: transições válidas passam, inválidas explodem com `IllegalArgumentException`
- [ ] Testes: criar training → mudar status → verificar histórico de eventos persistido

**Critério de saída:** state machine 100% testada. CRUD do training funcional.

### Fase 2 — Produtor: upload + publish (estimativa: 1 dia)

**Objetivo:** Upload sobe pro MinIO, publica na queue, persiste estado QUEUED.

- [ ] `BlobStorage` interface + `MinioBlobStorage` com `upload`, `download`, `delete`, `presignedUrl`
- [ ] `QueueTopology.declare()` invocado no startup
- [ ] `TrainingPublisher` com `publish(message)`
- [ ] `UploadRoute` reformulado:
  1. Valida tamanho/encoding
  2. Gera UUID
  3. Sobe pro `corpus-uploads/{uuid}.txt`
  4. Cria training no SQLite com QUEUED
  5. Publica mensagem
  6. Retorna `{training_id, status_url, progress_url}`
- [ ] `GET /training/{id}/progress` retornando HTML simples com placeholder
- [ ] `GET /api/training/{id}` retornando JSON com status + events

**Critério de saída:** upload pelo browser cria training QUEUED, mensagem visível no RabbitMQ management UI, blob visível no MinIO console.

### Fase 3 — Consumer: pipeline completo (estimativa: 1.5 dias)

**Objetivo:** Worker pool processando mensagens, executando Tessera+Mosaic, persistindo cada transição.

- [ ] `TrainingConsumer` com worker pool (N threads = `CONSUMER_CONCURRENCY`)
- [ ] Cada worker: receber mensagem → idempotência check → loop pelo pipeline
- [ ] Pipeline integrado: download blob → tokenizar (Tessera) → embedar (Mosaic) → indexar
- [ ] Cada etapa: atualiza SQLite + registra evento + log estruturado
- [ ] Upload de modelos treinados pro `trained-models/{trainingId}/...`
- [ ] Cleanup: deleta `/tmp/{uuid}.txt` no finally, deleta blob original ao terminar
- [ ] Manual ack em sucesso, nack sem requeue em falha (vai pra DLQ)
- [ ] Graceful shutdown: SIGTERM espera workers terminarem mensagem atual antes de sair
- [ ] Teste de integração: end-to-end upload → READY

**Critério de saída:** ciclo completo funcionando, DLQ recebendo mensagens em falhas simuladas, sem leaks de arquivo em `/tmp`.

### Fase 4 — Frontend: progresso + dashboard (estimativa: 1.5 dias)

**Objetivo:** Usuário vê progresso em tempo real, dashboard de trainings funcional.

- [ ] Página `/training/{id}/progress`:
  - Timeline visual com checkmarks por etapa concluída
  - Spinner na etapa atual
  - Auto-redirect pra `/explore/{id}` quando READY
  - Mostra mensagem de erro se FAILED
- [ ] Página `/trainings` (nova aba "Trainings" no menu):
  - Tabela com colunas: ID curto, Status (badge colorido), Criado há, Etapa há, Ações
  - Filtros: status (multi), data (today/last hour/all)
  - Botão "View details" expande timeline
  - Auto-refresh a cada 3s via polling em `/api/trainings`
- [ ] CSS pra status badges (cores da máquina de estados)
- [ ] JS modularizado (não tudo num `app.js` gigante)

**Critério de saída:** UX completa do upload ao READY. Dashboard útil.

### Fase 5 — Observabilidade básica (estimativa: 0.5 dia)

**Objetivo:** Debug e monitoring viáveis.

- [ ] Logback config com JSON encoder (logstash)
- [ ] Plugin Ktor `CallId` ou MDC manual injetando `training_id` no contexto
- [ ] Todos logs do consumer incluem `training_id`
- [ ] `MetricsRegistry` com `AtomicLong` counters: queued, completed, failed
- [ ] `/metrics` formato texto simples (`# HELP`, `# TYPE`, valores)

**Critério de saída:** `docker logs app` mostra JSON parseável. `/metrics` retorna texto com contadores reais.

### Fase 6 — Documentação + vídeo (estimativa: 1 dia)

**Objetivo:** README + ARCHITECTURE + vídeo atualizado.

- [ ] README atualizado:
  - Badge de versão pra v0.1.0
  - **Diagrama de arquitetura** (Mermaid) mostrando: Browser → App → MinIO + RabbitMQ + SQLite, Consumer ← RabbitMQ
  - Seção "Architecture" explicando os 4 componentes
  - Seção "Why this architecture?" com 3 trade-offs justificados
  - Quick start: `docker-compose up` ainda em 1 comando
  - Link pro management UI do RabbitMQ e console do MinIO
- [ ] ARCHITECTURE.md detalhado:
  - Por que MinIO vs filesystem
  - Por que SQLite vs Postgres
  - Por que Exposed vs JDBC puro
  - Por que polling vs SSE (e quando trocar)
  - Por que DLQ + manual acks
  - Diagrama de máquina de estados
- [ ] CHANGELOG.md detalhando breaking changes da v0.1.0
- [ ] **Vídeo atualizado** (60-90s):
  - 0-15s: " era um demo simples. v0.1.0 transforma em arquitetura realista"
  - 15-30s: upload do corpus, mostrar 202 com training_id
  - 30-50s: tab Trainings mostrando status mudando ao vivo, mostrar RabbitMQ UI e MinIO console
  - 50-70s: training READY, vai pra explore, faz busca
  - 70-90s: simular falha (corpus malformado), mostrar DLQ recebendo a mensagem
- [ ] Comprimir vídeo: `ffmpeg -i raw.mov -vcodec libx264 -crf 28 -preset slow -vf "scale=1280:720" docs/demo-v0.1.0.mp4`

**Critério de saída:** README com diagrama, vídeo gravado e embarcado, ARCHITECTURE.md publicado.

### Fase 7 — Release v0.1.0 (estimativa: 0.5 dia)

**Objetivo:** Imagem Docker publicada, tag criada.

- [ ] Commit final em `main` com `feat!:` pra forçar major bump (v0.0.x → v0.1.0)
- [ ] Validar workflow de release:
  1. Calculou v0.1.0
  2. Atualizou versões nos arquivos
  3. Buildou imagem
  4. Publicou `ghcr.io/hectorifc/nlp-kotlin-playground:v0.1.0` e `:latest`
  5. Criou tag git
  6. Criou GitHub Release com changelog
- [ ] Testar `docker pull` + `docker-compose up` em máquina limpa
- [ ] Atualizar badges no README

**Critério de saída:** Em máquina virgem, `git clone && docker-compose up` resulta em playground v0.1.0 totalmente funcional.

**Total: 7 dias úteis (~32-40h de trabalho focado)**

---

## 6. Armadilhas Conhecidas (LER ANTES DE CODAR)

### 6.1. `depends_on: condition: service_healthy` é Docker Compose v3.9+

Sem isso, app sobe antes de MinIO/RabbitMQ, falha conexão, crasha, restart loop. Confirmar versão do compose file (`version: '3.9'` ou superior, ou simplesmente sem versão em compose v2 syntax).

### 6.2. Mensagem RabbitMQ pode ser entregue 2x

Idempotência é **obrigatória**, não opcional. Sem o check da seção 4.8, restart do consumer mid-processing causa re-processamento + corrupção. **Sempre** verificar estado atual antes de processar.

### 6.3. SQLite + writes paralelos = SQLITE_BUSY

SQLite tem lock global pra writes. Se 2 consumers updateam status simultaneamente, um pode dar erro. Soluções:
- WAL mode: `PRAGMA journal_mode=WAL;` permite múltiplos readers + 1 writer
- Retry com exponential backoff em `SQLiteBusyException`
- Pool de conexões com 1 connection só (serializa writes naturalmente)

Recomendo: WAL mode + retry no repository.

### 6.4. Exposed initialization

`Database.connect()` deve rodar **uma vez** no startup, antes de qualquer query. `transaction { }` blocks em handlers de rota — sem isso, "no transaction in context".

### 6.5. Connection pooling com RabbitMQ

`Connection` é caro de abrir. **Reutilize**: uma `Connection` por aplicação, múltiplos `Channel`s (um por thread/consumer worker). **Nunca** abrir connection por mensagem.

### 6.6. MinIO bucket policies

Por default buckets são privados (precisa de auth). Pra usuário acessar URL direto, precisaria signed URL ou tornar bucket public. **Decisão atual**: corpus blob não é acessado por usuário, só pelo consumer interno. Frontend nunca recebe URL do MinIO diretamente. Decisão original do user de "receber URL no frontend" foi simplificada — frontend recebe só `training_id`, MinIO fica invisível pro browser.

### 6.7. Cleanup de tempfile com `try/finally`

`File.deleteOnExit()` **não funciona** se JVM for morta com SIGKILL. Sempre `try { ... } finally { Files.delete... }`. E confirme com `du -sh /tmp/` periodicamente em dev se não tem leak.

### 6.8. DLQ pode entupir silenciosamente

Mensagens em FAILED ficam na DLQ indefinidamente. Sem monitoramento, em produção viraria problema. Pra v0.1.0, basta documentar no README "DLQ deve ser inspecionada manualmente no RabbitMQ management UI". Pra v0.2 considerar TTL na DLQ ou alerta.

### 6.9. Mensagens persistidas vs não-persistidas

Por default, mensagens em queue durável são **não-persistidas em disco**. Pra sobreviver crash do broker, mensagem precisa ser publicada com `MessageProperties.PERSISTENT_TEXT_PLAIN`. **Sempre** publicar persistente:

```kotlin
channel.basicPublish(EXCHANGE, ROUTING_KEY, MessageProperties.PERSISTENT_TEXT_PLAIN, body)
```

### 6.10. Graceful shutdown

Container recebe SIGTERM. Consumer no meio de processamento: termina mensagem (ack ou nack) **antes** de fechar connection. Implementar shutdown hook:

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    log.info("Shutting down consumer pool...")
    consumerPool.shutdown(timeout = 30.seconds)
    log.info("Closing connections...")
    rabbitConnection.close()
    sqliteConnection.close()
})
```

### 6.11. Logback com encoder Logstash

Encoder precisa ser explicitamente declarado no `logback.xml`. Sem isso, logs saem em formato texto convencional, não JSON. Validar com `docker logs app | jq .` — se quebrar, encoder não está configurado.

### 6.12. Race condition no marcar EXPIRED

Cron job (ou scheduled task no Ktor) marca trainings EXPIRED. Se rodar enquanto consumer ainda está processando, pode marcar uma training em progresso como EXPIRED. **Solução**: state machine não permite transição de estados intermediários pra EXPIRED, só de READY. Marcar EXPIRED só pra status READY com `expires_at < now`.

### 6.13. Versão de Exposed muda APIs significativamente

Versões 0.40 → 0.45 → 0.50 → 0.55 tiveram breaking changes na API DSL. **Pinne versão exata** no `build.gradle.kts`. Não use ranges.

### 6.14. Logs sem correlation ID = pesadelo de debug

Quando 2 consumers processam 2 trainings em paralelo, logs se misturam. Sem `training_id` no MDC, impossível seguir um fluxo específico. **Cada log do consumer deve incluir training_id**.

### 6.15. SQLite no Docker: permissões do volume

Container roda como non-root, mas volume mount pode estar como root:root, dando "readonly database". Solução no Dockerfile: `chown -R appuser:appuser /data` antes do entrypoint, e no compose garantir que volume é gravável.

### 6.16. JitPack pode falhar build da imagem Docker

Tessera e Mosaic vêm via JitPack. Se JitPack está lento ou caiu, build do Docker quebra. **Mitigação**: cachear Gradle dependencies no Dockerfile multi-stage:

```dockerfile
COPY build.gradle.kts settings.gradle.kts gradle.properties /build/
COPY gradle /build/gradle
RUN ./gradlew dependencies --no-daemon || true   # baixa deps, ignora erro
COPY . /build
RUN ./gradlew installDist --no-daemon
```

### 6.17. `BlobStorage.download` direto pro `OutputStream`

Tentação: passar `tempFile.outputStream()` direto pra MinIO client. **Cuidado**: alguns clients fazem retry, e podem reescrever stream do zero, corrompendo. Validar comportamento do `io.minio` SDK. Se necessário, baixar tudo em `ByteArrayOutputStream` primeiro, depois escrever em disco.

### 6.18. Cobertura de testes em consumers é difícil

Testar consumer envolve: RabbitMQ em memória ou testcontainers, mock de MinIO, mock de Tessera/Mosaic, fixtures de eventos SQLite. **Aceitação de relaxar para 60%** está documentada (seção 3.2). Cobre os repositories e state machine bem, mocka o resto.

---

## 7. Recursos e Referências

### 7.1. Documentação técnica

- **Ktor + Exposed**: https://ktor.io/docs/server-integrate-database.html
- **Exposed DSL**: https://github.com/JetBrains/Exposed/wiki
- **RabbitMQ Java client**: https://www.rabbitmq.com/tutorials/tutorial-one-java
- **RabbitMQ DLX guide**: https://www.rabbitmq.com/dlx.html
- **MinIO Java SDK**: https://min.io/docs/minio/linux/developers/java/minio-java.html
- **SQLite WAL mode**: https://www.sqlite.org/wal.html

### 7.2. Projetos irmãos (referência canônica)

- **Tessera**: https://github.com/HectorIFC/tessera (estrutura de workflows, configs)
- **Mosaic**: https://github.com/HectorIFC/mosaic (estrutura de workflows, configs)
- **nlp-kotlin-playground**: o próprio repo, branch `main`

### 7.3. Padrões de referência (consulta em caso de dúvida)

- **Outbox pattern**: pra garantir atomicidade entre SQLite write e RabbitMQ publish. **Não usar nessa v0.1.0**, mas documentar como melhoria futura
- **Saga pattern**: pra coordenar pipelines longos. Overkill aqui, mas mencionar em ARCHITECTURE
- **Idempotency keys**: usado em consumer (seção 4.8). Padrão clássico, vale linkar referências em "further reading"

---

## 8. Workflow com Claude Code

### 8.1. Como você (Claude Code) deve operar

1. **Leia este PRD inteiro antes de qualquer ação.**
2. Confirme entendimento — em particular:
   - É **breaking change major** (v0.0.x → v0.1.0)
   - Tessera e Mosaic continuam como dependências JitPack
   - **Sem mudanças** em Tessera ou Mosaic — apenas no playground
   - Idempotência do consumer é **obrigatória**, não opcional
   - Cleanup de `/tmp` é **obrigatório**, não opcional
3. Trabalhe **fase por fase**, na ordem. Não pule.
4. Ao começar uma fase, mostre o plano específico antes de codar.
5. Commit frequente, Conventional Commits com escopo.
6. Rode `docker-compose up` ao final de cada fase pra validar que infra continua subindo.
7. Após cada fase, status report + aguardar confirmação.

### 8.2. Convenções de código

- Imutabilidade por padrão (`val`)
- `internal` para tudo que não é API pública (não há "API pública" exposta aqui, mas a regra ajuda em coesão)
- Coroutines pra workers do consumer (não threads diretas)
- Try-with-resources / `use {}` em tudo que tem `close()`
- Logs com `LoggerFactory.getLogger(MyClass::class.java)` ou `KotlinLogging`

### 8.3. Convenções de git

Conventional Commits, mas com `feat!` para mudanças breaking:

- `feat!(api): change upload to async with training_id`
- `feat(persistence): add SQLite schema with Exposed`
- `feat(messaging): wire RabbitMQ publisher + consumer`
- `feat(storage): integrate MinIO client`
- `feat(ui): add Trainings dashboard tab`
- `feat(observability): add structured JSON logging`
- `fix(consumer): cleanup tempfile in finally block`
- `docs: update README with v0.1.0 architecture`
- `build(deps): add Exposed, MinIO, RabbitMQ clients`
- `test(state-machine): cover all valid transitions`

### 8.4. Versionamento

- `0.0.1` → estado atual da `main`
- `0.1.0-SNAPSHOT` → durante desenvolvimento (Fases 0-6)
- `0.1.0` → release final (Fase 7)

Como há breaking changes, `feat!:` ou `BREAKING CHANGE:` no footer força major bump.

---

## 9. Comunicação e Bloqueios

### 9.1. Quando perguntar ao usuário

- Decisões fora do escopo
- Trade-offs significativos (especialmente em UX do dashboard)
- Antes de qualquer mudança em decisão fechada da seção 1.4
- Ao final de cada fase

### 9.2. Quando NÃO perguntar

- Detalhes de implementação cobertos pelo PRD
- Quais testes adicionar — adicione todos que fizerem sentido
- Estética de código

### 9.3. Status report ideal ao final de cada fase

```
✅ Fase X concluída.

Implementado:
- item 1
- item 2

Testes adicionados:
- N testes, todos passando
- Cobertura atual: X%

Critérios de saída:
- [x] critério A
- [x] critério B

Próximos passos: iniciando Fase Y. Posso prosseguir?

Notas / surpresas durante implementação:
- ponto 1
- ponto 2
```

---

## 10. Apêndice — Glossário

- **Blob**: arquivo binário armazenado no MinIO (corpus uploadado ou modelo treinado)
- **DLX**: Dead Letter Exchange. Exchange que recebe mensagens rejeitadas
- **DLQ**: Dead Letter Queue. Queue conectada ao DLX onde falhas acumulam pra inspeção
- **Manual ack**: consumer explicitamente confirma processamento (vs auto-ack que confirma no recebimento)
- **Exchange durável**: sobrevive a restart do broker (persistido em disco)
- **Mensagem persistente**: sobrevive a restart do broker (persistida em disco, junto com a queue durável)
- **MDC**: Mapped Diagnostic Context — mecanismo do SLF4J pra passar contexto entre threads em logs estruturados
- **State machine**: conjunto restrito de transições permitidas entre estados
- **Idempotência**: processar a mesma operação 2x produz o mesmo resultado da primeira (sem efeito colateral cumulativo)
- **WAL mode**: Write-Ahead Logging do SQLite, permite leituras concorrentes durante escrita
- **Training**: instância completa de processamento de um corpus (com seu próprio UUID, estado, blob, modelo)

---

## 11. Checklist mestre

- [ ] Fase 0: Setup compose multi-container + dependências
- [ ] Fase 1: Persistência SQLite + state machine
- [ ] Fase 2: Produtor (upload + publish)
- [ ] Fase 3: Consumer (pipeline + cleanup)
- [ ] Fase 4: Frontend (progress page + dashboard)
- [ ] Fase 5: Observabilidade
- [ ] Fase 6: Documentação + vídeo
- [ ] Fase 7: Release v0.1.0
- [ ] Todos critérios da seção 3 atingidos
- [ ] Imagem Docker pública v0.1.0 funcional em máquina limpa
- [ ] README com diagrama de arquitetura
- [ ] ARCHITECTURE.md detalhado
- [ ] CHANGELOG explicando breaking changes

**Quando esse checklist estiver completo, o playground demonstra capacidade arquitetural sênior: queue-based processing, state machine, blob storage, observabilidade. O ecossistema NLP em Kotlin ganha maturidade de "demo" para "miniatura de sistema realista".**

---

## 12. Considerações para o futuro (post-v0.1.0)

- **v0.2.0**: SSE substituindo polling
- **v0.3.0**: DLQ visualization na UI com botão de requeue
- **v0.4.0**: métricas Prometheus completas + Grafana dashboard
- **Tapestry** (projeto separado): treinar embeddings de verdade, transformar Mosaic de lookup table em embedding semântico
- **Markov-kt** (projeto separado): geração de texto via cadeia de Markov pra "chat retrô"

Mas tudo isso é depois. Foco no presente: v0.1.0, arquitetura distribuída, demonstrável em 1 comando Docker.
