# PRD — nlp-kotlin-playground

> **Playground web interativo demonstrando o pipeline Tessera + Mosaic em ação.**
>
> Terceiro projeto do ecossistema: onde [Tessera](https://github.com/HectorIFC/tessera) e [Mosaic](https://github.com/HectorIFC/mosaic) finalmente encontram um usuário final.

---

## 📋 Documento de requisitos para implementação assistida via Claude Code

Este PRD descreve o projeto completo, decisões já tomadas, escopo, plano de fases, critérios de aceitação e armadilhas conhecidas. Foi construído após discussão detalhada sobre objetivos e restrições. **Leia tudo antes de começar.**

---

## 1. Contexto e Motivação

### 1.1. Quem sou eu e por que esse projeto existe

Sou um desenvolvedor estudando LLMs **fazendo do zero**, e este é o **terceiro projeto** de um pipeline de NLP em Kotlin puro:

1. **[Tessera](https://github.com/HectorIFC/tessera)** (v0.0.7) — tokenizer BPE byte-level
2. **[Mosaic](https://github.com/HectorIFC/mosaic)** (v0.0.4) — embeddings (lookup table)
3. **nlp-kotlin-playground** (este projeto) — **demonstração visual interativa** do pipeline

### 1.2. Objetivo principal: portfolio para processos seletivos

**Esse projeto existe para portfólio.** O objetivo é que um recrutador técnico possa, em menos de 60 segundos:

1. Clicar no link do projeto
2. Ver um **vídeo curto** (gravado por mim) demonstrando o pipeline funcionando
3. Entender que existem 3 projetos relacionados, todos meus
4. Opcionalmente: rodar o projeto na própria máquina via Docker

Portanto, **prioridades nessa ordem**:

1. **Vídeo demonstrativo no README** (gravado por mim, será o "produto" principal)
2. **Aplicação funcional** (web app que faz o que o vídeo mostra)
3. **Docker** (pra recrutador rodar em 1 comando)
4. **Versionamento automatizado** (mesma stack de CI/CD do Tessera e Mosaic)

### 1.3. Natureza do projeto: APLICAÇÃO, não biblioteca

Diferente de Tessera e Mosaic, **nlp-kotlin-playground é uma aplicação web**, não uma biblioteca:

- Tem backend (Kotlin/Ktor) e frontend (HTML/CSS/JS estático ou template-engine simples)
- **Consome** Tessera e Mosaic como dependências via JitPack
- Não vai pro Maven/JitPack como artefato (é app, não lib)
- Distribuição é via **imagem Docker** publicada no GitHub Container Registry (`ghcr.io`)
- Versionamento segue SemVer igual aos outros, mas o "release" é a imagem Docker, não um JAR

### 1.4. Princípios não-negociáveis

- **Kotlin/JVM puro** no backend (sem Node, sem Python). Coerência com o ecossistema.
- **Tessera e Mosaic como dependências via JitPack.** Não reimplementar nada deles.
- **Frontend simples e estático** (HTML + CSS + vanilla JS, ou htmx). Sem React/Vue/etc — não cabe no escopo de 2 dias, e adicionaria complexidade sem ganho proporcional para o objetivo.
- **Honestidade técnica**: o playground demonstra o pipeline real. Embeddings são lookup table não treinada (limitação assumida e documentada). Sem chat fake, sem similaridades inventadas.
- **Mesma stack de qualidade** do Tessera e Mosaic: ktlint, detekt, kover (se aplicável), GitHub Actions, dependabot, PR template.

### 1.5. Decisões arquiteturais já tomadas (NÃO RE-DECIDIR)

Essas decisões já foram tomadas após análise. **Não as questione** — siga-as:

1. **Caminho 1 puro: playground de exploração, SEM CHAT.** Discutimos longamente; chat seria desonesto sem treino real de embeddings, que é projeto futuro. O playground demonstra o pipeline real: upload → tokenização → embeddings → exploração visual.
2. **Híbrido para corpus**: usuário pode **escolher um corpus pré-treinado** da lista (3-5 opções curadas) OU **fazer upload do próprio corpus** (com aviso de tempo).
3. **Backend Kotlin obrigatório.** Tessera e Mosaic são Kotlin/JVM puro — não rodam no browser. Framework: **Ktor** (leve, idiomático Kotlin, simples).
4. **Frontend HTML estático servido pelo Ktor.** Sem SPA framework. Vanilla JS para interatividade. Templating opcional (Mustache ou Pebble) ou string interpolation simples.
5. **Docker obrigatório.** Imagem multi-stage (build com Gradle + runtime JRE), publicada no GitHub Container Registry via workflow automatizado.
6. **Vídeo gravado pelo usuário** será embarcado no README. Não é responsabilidade do código gerar o vídeo — só prover o app que será gravado.
7. **Paleta visual**: combinar com Tessera (roxo/verde) e Mosaic (laranja/preto/branco). Sugestão: **paleta neutra dark** que sirva de "ponte" entre os dois — preto + branco + cinza + um detalhe de roxo OU laranja. Decisão final na Fase 4.
8. **Tessera v0.0.7 e Mosaic v0.0.4 fixados** como dependências iniciais.

---

## 2. Escopo

### 2.1. Dentro do escopo (MUST HAVE)

#### Funcionalidade

- **Tela inicial**: escolha entre "Use pre-trained model" ou "Upload your own corpus"
- **Corpus pré-treinados curados** (3-5): tokenizer + embeddings já gerados, incluídos no JAR
- **Upload de corpus** (.txt UTF-8, limite de tamanho razoável, ex: 2MB): backend treina BPE + cria embeddings, mostra progress
- **Tela de exploração** após pipeline pronto:
  - **Busca semântica**: usuário digita uma frase, vê top-K mais similares no corpus (cosine similarity sobre mean-pooled embeddings)
  - **Visualização de tokens**: usuário digita uma frase, vê tokens e IDs (tokenização Tessera em ação)
  - **Comparação de pares**: usuário seleciona 2 trechos do corpus, vê score de similaridade
- **Disclaimer permanente e claro**: "Embeddings here are not trained (Mosaic is a lookup table). Similarities reflect random vectors and serve to demonstrate the pipeline mechanics, not semantic quality."

#### Infraestrutura

- Backend Kotlin/Ktor + frontend HTML/CSS/JS estático
- `Dockerfile` multi-stage funcional, imagem pequena (< 250 MB ideal)
- `docker-compose.yml` para rodar localmente em 1 comando
- Imagem publicada no GHCR (`ghcr.io/hectorifc/nlp-kotlin-playground`)
- Workflows GitHub Actions: CI (build + test) + Release (versionamento + Docker publish)
- Dependabot configurado
- PR template

#### Documentação

- **README com vídeo embarcado** (formato MP4 hostado no GitHub via `Releases` ou em `docs/`)
- README explica: o que é, como rodar (Docker), screenshots, link pros projetos pais
- ARCHITECTURE.md explicando como Tessera + Mosaic se conectam aqui
- Disclaimer sobre embeddings não-treinados em local visível

### 2.2. Fora do escopo (NÃO FAZER)

- **Chat / geração de texto** (discutido extensivamente — fora de escopo)
- Treinamento de embeddings (Word2Vec, etc) — projeto futuro
- Modelo de linguagem (transformer, RNN) — projeto futuro
- Autenticação / contas de usuário
- Persistência de sessões entre acessos (cada upload é efêmero, vive na sessão JVM)
- Multi-usuário simultâneo robusto (single instance, sem fila de jobs)
- SPA com React/Vue/Svelte
- Banco de dados (tudo em memória)
- HTTPS próprio (deploy externo é responsabilidade do operador; Docker expõe HTTP)
- Internacionalização (UI em inglês, ponto final)
- Mobile-app
- Análise / telemetria

### 2.3. Stretch goals (NICE TO HAVE, só se sobrar tempo após Fase 5)

- Visualização 2D dos embeddings (redução de dimensionalidade via PCA caseiro)
- Heatmap dos vetores na UI
- Histórico de buscas na sessão
- Exportar resultados como JSON
- Suporte a upload de arquivos ZIP (múltiplos textos)

---

## 3. Definição de "Pronto" (Done)

O projeto é considerado finalizado quando **todos** estes critérios forem atendidos:

### 3.1. Critérios funcionais

- [ ] Backend Ktor sobe sem erro via `./gradlew run`
- [ ] Página inicial carrega em < 2 segundos
- [ ] Escolher um modelo pré-treinado leva à tela de exploração em < 1 segundo
- [ ] Upload de corpus de 100KB completa o pipeline em < 30 segundos
- [ ] Busca semântica retorna top-5 resultados em < 500ms (após pipeline pronto)
- [ ] Tokenização visualizada bate exatamente com o output da CLI do Tessera (mesma versão)
- [ ] Disclaimer sobre embeddings não-treinados aparece em destaque na tela de exploração

### 3.2. Critérios de Docker

- [ ] `docker build .` constrói imagem sem erro
- [ ] `docker run -p 8080:8080 ghcr.io/hectorifc/nlp-kotlin-playground:latest` sobe a app
- [ ] Imagem final tem < 300 MB
- [ ] `docker-compose up` funciona em 1 comando, sem variáveis obrigatórias
- [ ] Workflow GitHub Actions publica imagem em GHCR a cada release

### 3.3. Critérios de portfolio

- [ ] **Vídeo de demonstração embarcado no README** (gravado pelo usuário após app pronto)
- [ ] README tem screenshots/GIFs do pipeline em ação
- [ ] README tem links cruzados para Tessera e Mosaic
- [ ] README tem badge de Docker pull e versão
- [ ] Sem dead links no README

### 3.4. Critérios de código

- [ ] `./gradlew test` passa
- [ ] `./gradlew ktlintCheck detekt` passa sem warnings
- [ ] Sem dependências runtime desnecessárias
- [ ] Estrutura de pacotes clara
- [ ] README de cada componente

### 3.5. Critérios de versionamento (espelhando Tessera/Mosaic)

- [ ] `.github/workflows/ci.yml` espelha o do Tessera
- [ ] `.github/workflows/release.yml` adaptado para publicar imagem Docker (não JAR)
- [ ] `.github/dependabot.yml` configurado (Gradle + Actions + Docker)
- [ ] `.github/pull_request_template.md` no mesmo formato
- [ ] `config/detekt/detekt.yml` adaptado
- [ ] Tag `v0.0.1` criada via release workflow

---

## 4. Especificação Técnica

### 4.1. Identidade do projeto

- **Nome:** nlp-kotlin-playground
- **Tagline:** "An interactive playground demonstrating the Tessera + Mosaic NLP pipeline in Kotlin."
- **Repositório:** `https://github.com/HectorIFC/nlp-kotlin-playground`
- **Imagem Docker:** `ghcr.io/hectorifc/nlp-kotlin-playground`
- **Package base:** `dev.nlpplayground`
- **Porta padrão:** 8080

### 4.2. Stack

- **Linguagem:** Kotlin 2.0+ (target JVM 21 — alinhado com Tessera/Mosaic)
- **Web framework:** Ktor 3.x (mais recente estável)
- **Templating:** Mustache (Ktor tem suporte first-class) OU strings com `${}` se simples o suficiente
- **Build:** Gradle com Kotlin DSL
- **Testes:** Kotest (consistência com Tessera/Mosaic)
- **Quality:** ktlint + detekt
- **Containerização:** Docker multi-stage (eclipse-temurin)
- **CI/CD:** GitHub Actions + GHCR
- **Dependências críticas via JitPack:**
  - `com.github.HectorIFC:tessera:tessera-core-v0.0.7`
  - `com.github.HectorIFC:mosaic:mosaic-core-v0.0.4`

### 4.3. Estrutura do projeto

```
nlp-kotlin-playground/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md                        # com vídeo embarcado
├── PRD.md
├── ARCHITECTURE.md                  # criado na Fase 6
├── CHANGELOG.md
├── LICENSE
├── .gitignore
├── .editorconfig
├── .dockerignore
├── Dockerfile                       # multi-stage
├── docker-compose.yml
│
├── .github/
│   ├── dependabot.yml               # Gradle + Actions + Docker
│   ├── pull_request_template.md
│   └── workflows/
│       ├── ci.yml                   # build + test + lint
│       └── release.yml              # tag + Docker publish
│
├── config/detekt/
│   └── detekt.yml
│
├── docs/
│   └── demo.mp4                     # vídeo gravado pelo usuário
│
├── pretrained/                      # corpora pré-treinados (incluídos no JAR)
│   ├── alice-in-wonderland/
│   │   ├── tessera.json
│   │   ├── mosaic.bin
│   │   ├── mosaic.bin.meta.json
│   │   └── corpus.txt               # original, para busca
│   ├── shakespeare-sonnets/
│   │   └── ...
│   └── kotlin-stdlib-docs/
│       └── ...
│
└── src/
    ├── main/
    │   ├── kotlin/dev/nlpplayground/
    │   │   ├── Application.kt              # entry point + Ktor setup
    │   │   ├── Routing.kt                  # define endpoints
    │   │   ├── pipeline/
    │   │   │   ├── PipelineService.kt      # orquestra Tessera + Mosaic
    │   │   │   ├── PretrainedLoader.kt     # carrega modelos do classpath
    │   │   │   ├── CorpusTrainer.kt        # treina BPE + cria embedding sob demanda
    │   │   │   └── SemanticSearch.kt       # busca top-K
    │   │   ├── session/
    │   │   │   └── SessionStore.kt         # cache em memória de pipelines ativos
    │   │   └── routes/
    │   │       ├── HomeRoute.kt
    │   │       ├── UploadRoute.kt
    │   │       ├── ExploreRoute.kt
    │   │       └── ApiRoute.kt             # endpoints JSON
    │   └── resources/
    │       ├── application.conf            # config Ktor
    │       ├── static/
    │       │   ├── css/main.css
    │       │   ├── js/app.js
    │       │   └── img/                    # logos cruzados
    │       └── templates/                  # se usar Mustache
    │           ├── home.mustache
    │           └── explore.mustache
    └── test/
        └── kotlin/dev/nlpplayground/
            ├── PipelineServiceTest.kt
            ├── RoutingTest.kt              # testes de rota via Ktor TestApplication
            └── PretrainedLoaderTest.kt
```

### 4.4. Endpoints HTTP

```
GET  /                              # home page (escolher modo)
GET  /pretrained                    # lista modelos pré-treinados (HTML ou JSON)
POST /upload                        # recebe corpus, retorna session ID
GET  /explore/{sessionId}           # tela de exploração
POST /api/search/{sessionId}        # body: {query: string, topK: int} → JSON top-K
POST /api/tokenize/{sessionId}      # body: {text: string} → JSON tokens + IDs
POST /api/similarity/{sessionId}    # body: {textA, textB} → JSON {score: float}
GET  /api/status/{sessionId}        # status do pipeline (training/ready/error)
GET  /health                        # liveness probe pra Docker
```

### 4.5. Pipeline interno (uso de Tessera e Mosaic)

#### Modo "pre-trained"

```kotlin
// PretrainedLoader.kt
fun load(name: String): Pipeline {
    val tokenizer = BpeTokenizer.load(classpathResource("pretrained/$name/tessera.json"))
    val embeddings = EmbeddingTable.load(classpathResource("pretrained/$name/mosaic.bin"))
    val corpusLines = classpathResource("pretrained/$name/corpus.txt").readLines()
    return Pipeline(tokenizer, embeddings, corpusLines)
}
```

#### Modo "upload"

```kotlin
// CorpusTrainer.kt
fun train(corpus: String): Pipeline {
    // 1. Treinar BPE com Tessera
    val tokenizer = Trainer(TrainingConfig(numMerges = 2000))
        .train(corpus)
    
    // 2. Criar embedding aleatório com vocab compatível
    val embeddings = EmbeddingTable.create(
        vocabSize = tokenizer.vocabSize,
        embeddingDim = 128,
        initializer = Initializer.uniformDefault(seed = 42L)
    )
    
    // 3. Quebrar corpus em "frases" pra indexar
    val sentences = corpus.split(Regex("[.!?\\n]+")).filter { it.length > 10 }
    
    return Pipeline(tokenizer, embeddings, sentences)
}
```

#### Busca semântica

```kotlin
// SemanticSearch.kt
fun search(pipeline: Pipeline, query: String, topK: Int = 5): List<SearchResult> {
    val combo = TesseraEmbeddings(pipeline.tokenizer, pipeline.embeddings)
    val queryVector = combo.encodeMeanPooled(query)
    
    return pipeline.sentences
        .map { sentence ->
            val sentenceVector = combo.encodeMeanPooled(sentence)
            val score = VectorOps.cosineSimilarity(queryVector, sentenceVector)
            SearchResult(sentence, score)
        }
        .sortedByDescending { it.score }
        .take(topK)
}
```

### 4.6. SessionStore (cache em memória)

```kotlin
// SessionStore.kt
internal class SessionStore {
    private val sessions = ConcurrentHashMap<String, Pipeline>()
    private val createdAt = ConcurrentHashMap<String, Instant>()
    
    fun create(pipeline: Pipeline): String {
        val id = UUID.randomUUID().toString()
        sessions[id] = pipeline
        createdAt[id] = Instant.now()
        return id
    }
    
    fun get(id: String): Pipeline? = sessions[id]
    
    /** Remove sessões com mais de 1 hora. Roda em scheduler do Ktor. */
    fun evictOld() {
        val cutoff = Instant.now().minus(Duration.ofHours(1))
        createdAt.entries.removeIf { (id, time) ->
            if (time.isBefore(cutoff)) {
                sessions.remove(id)
                true
            } else false
        }
    }
}
```

**Decisões importantes**:
- Sessões em memória (mata simplicidade). Se reiniciar Docker, sessões somem — aceitável pra playground.
- Eviction básica (1 hora) pra não vazar memória em uso prolongado.
- Não tenta ser thread-safe ao nível de "dois usuários treinando simultaneamente o mesmo upload" — esse cenário é raro o suficiente para ignorar.

### 4.7. Corpora pré-treinados

**3 corpora curados, incluídos no JAR via `src/main/resources/pretrained/`**:

#### Corpus 1 — "alice-in-wonderland"
- Texto completo de Alice no País das Maravilhas (domínio público, ~150KB)
- Variado, narrativo, vocabulário rico
- Demonstra busca em texto literário

#### Corpus 2 — "shakespeare-sonnets"
- Sonetos de Shakespeare (domínio público, ~100KB)
- Linguagem poética, vocabulário arcaico
- Demonstra que tokenizer lida com inglês antigo

#### Corpus 3 — "kotlin-stdlib-docs"
- Concatenação de comentários KDoc da stdlib do Kotlin (~200KB)
- Conteúdo técnico
- Demonstra busca em domínio técnico — relevante pra audiência do projeto

Cada corpus é pré-treinado **uma vez localmente** (script `scripts/pretrain.kts` ou similar), gerando `tessera.json`, `mosaic.bin` e `mosaic.bin.meta.json`. Esses arquivos vão **commitados** no repo, na pasta `pretrained/`. **NÃO regenerar a cada build.**

Total estimado: 3 × (150KB texto + 50KB tessera.json + 5MB mosaic.bin) ≈ 16-20 MB no JAR. Aceitável.

### 4.8. Dockerfile

**Multi-stage build**:

```dockerfile
# Stage 1: build
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
COPY . .
RUN ./gradlew --no-daemon installDist

# Stage 2: runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/build/install/nlp-kotlin-playground /app/
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1
ENTRYPOINT ["/app/bin/nlp-kotlin-playground"]
```

**Considerações**:
- `eclipse-temurin:21-jre-jammy` é base oficial mantida pela Adoptium
- Multi-stage corta ~500MB de JDK do final
- `installDist` do plugin `application` gera estrutura `bin/` + `lib/` pronta
- Healthcheck conecta no `/health` endpoint do Ktor

### 4.9. `.github/workflows/ci.yml`

Adaptado do Tessera, simplificado pra app (sem `koverVerify`, sem publicação Maven). Jobs:

**Job `test`**:
- Checkout
- Setup Java 21 Temurin
- Setup Gradle
- `./gradlew test`

**Job `quality`**:
- `./gradlew ktlintCheck detekt`

**Job `docker-build`** (apenas em PRs, não publica):
- `docker build -t nlp-kotlin-playground:pr-${{ github.event.number }} .`
- Smoke test: `docker run -d -p 8080:8080`, então `curl /health`

### 4.10. `.github/workflows/release.yml`

Adaptado do Tessera, **mas publica imagem Docker em vez de JAR**. Fluxo:

1. Push pra `main`
2. Determinar próxima versão via `mathieudutour/github-tag-action` (dry-run primeiro)
3. Abort se versão não mudou ou se tag já existe
4. Atualizar versão em: `gradle.properties`, `README.md`, `Dockerfile` label
5. Build + test localmente
6. Build Docker image com tags `latest` e `vX.Y.Z`
7. Login GHCR via `docker/login-action`
8. Push imagem com ambas as tags
9. Commitar version bump com `[skip ci]`
10. Criar git tag (modo real)
11. Criar GitHub Release

**Atenção**: imagem Docker precisa de permissão `packages: write`. Já está no workflow do Tessera mas confirme.

### 4.11. `.github/dependabot.yml`

Adaptado, com adição de Docker:

```yaml
version: 2
updates:
  - package-ecosystem: github-actions
    directory: /
    schedule: { interval: weekly, day: monday }
    labels: [dependencies, github-actions]
    commit-message: { prefix: "build(deps)" }

  - package-ecosystem: gradle
    directory: /
    schedule: { interval: weekly, day: monday }
    labels: [dependencies, gradle]
    commit-message: { prefix: "build(deps)" }
    groups:
      kotlin: { patterns: ["org.jetbrains.kotlin*"] }
      ktor: { patterns: ["io.ktor*"] }
      kotest: { patterns: ["io.kotest*"] }

  - package-ecosystem: docker
    directory: /
    schedule: { interval: weekly, day: monday }
    labels: [dependencies, docker]
    commit-message: { prefix: "build(deps)" }
```

### 4.12. UX da página de exploração

A tela principal após pipeline pronto. **Layout sugerido (single page)**:

```
┌─────────────────────────────────────────────────────────────┐
│  nlp-kotlin-playground   |   Tessera + Mosaic               │
│  Active corpus: alice-in-wonderland  |  [Change corpus]     │
├─────────────────────────────────────────────────────────────┤
│  ⚠ Note: Embeddings are not trained. Similarities reflect   │
│    random vectors and demonstrate pipeline mechanics only.  │
├─────────────────────────────────────────────────────────────┤
│  TAB: [Search] [Tokenize] [Compare]                         │
├─────────────────────────────────────────────────────────────┤
│  ── Search tab ──                                           │
│  ┌────────────────────────────────────────────┐ [Search]    │
│  │ Type a phrase...                           │             │
│  └────────────────────────────────────────────┘             │
│                                                             │
│  Top 5 most similar sentences in the corpus:                │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 1. "Alice was beginning to get very tired..."       │    │
│  │    score: 0.847  ████████████████░░░                │    │
│  │ 2. "...and what is the use of a book..."            │    │
│  │    score: 0.731  ██████████████░░░░░                │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                             │
│  ── Tokenize tab ──                                         │
│  Shows tokens + IDs for any input text                      │
│                                                             │
│  ── Compare tab ──                                          │
│  Two text boxes side-by-side, shows similarity score        │
└─────────────────────────────────────────────────────────────┘
```

### 4.13. Disclaimer (texto exato a usar)

Aparece em destaque na home E na tela de exploração:

> **About these results:** Mosaic provides embeddings as a *lookup table* — the vectors are randomly initialized, not trained. The pipeline (tokenization → vector lookup → mean pooling → cosine similarity) is real and identical to production-grade pipelines, but **without training, the similarities reflect random structure**, not semantic meaning. Training (Word2Vec / GloVe / etc) is a separate future project.

### 4.14. `.gitignore`

O arquivo `.gitignore` na raiz do repositório **deve ser exatamente este**. Cobre Kotlin/Gradle + Ktor + Docker + artefatos específicos deste projeto:

```gitignore
# ===== Gradle =====
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
!**/src/**/build/
gradle-app.setting
.gradletasknamecache

# ===== IntelliJ IDEA / Android Studio =====
.idea/
*.iml
*.ipr
*.iws
out/

# ===== VS Code =====
.vscode/
*.code-workspace

# ===== Eclipse =====
.classpath
.project
.settings/
bin/

# ===== macOS =====
.DS_Store
.AppleDouble
.LSOverride

# ===== Windows =====
Thumbs.db
Thumbs.db:encryptable
ehthumbs.db
Desktop.ini
$RECYCLE.BIN/

# ===== Linux =====
*~
.directory
.Trash-*

# ===== Kotlin / JVM =====
*.class
*.jar
*.war
*.ear
*.nar
hs_err_pid*
replay_pid*

# ===== Logs e temporários =====
*.log
*.tmp
*.bak
*.swp
*.swo

# ===== Docker =====
# Volumes locais que possam aparecer durante dev
.docker/
docker-volumes/
# Override files de docker-compose para configs locais
docker-compose.override.yml
docker-compose.local.yml

# ===== Específicos do nlp-kotlin-playground =====

# Uploads de usuário em runtime — NUNCA commitar
# (uploads ficam em memória, mas se um dia o app persistir em disco para debug, evitar commit)
uploads/
tmp-uploads/
*.upload.tmp

# Cache de download de corpora externos (Project Gutenberg etc)
# Os corpora TRATADOS em pretrained/ são commitados (caso especial), mas
# os downloads raw temporários do script de pretrain não.
.corpus-cache/
*.corpus.raw

# Artefatos gerados pelo script scripts/pretrain.kts em modo dev
# (os finais em pretrained/ SÃO commitados — ver exceção abaixo)
scratch/
.pretrain-tmp/

# Snapshots de sessão se um dia for habilitada persistência local pra debug
sessions/
*.session.json

# Credenciais de publicação (NUNCA commitar)
local.properties
gradle.properties.local
.env
.env.local
.env.*.local
ghcr-token.txt
**/secrets.properties

# Dependency caches
.kotlin/
kotlin-js-store/

# Vídeos brutos pré-edição (só o demo.mp4 final em docs/ deve ser commitado)
*.mov
docs/*.raw.mp4
docs/demo-raw.*
```

#### Exceções importantes (o que DEVE ser commitado)

Diferente da regra default ("artefatos gerados ficam de fora"), estes arquivos **devem** estar no repositório:

- **`pretrained/*/tessera.json`** — tokenizers pré-treinados, fazem parte do JAR
- **`pretrained/*/mosaic.bin`** — embeddings pré-treinados, fazem parte do JAR
- **`pretrained/*/mosaic.bin.meta.json`** — metadata dos embeddings
- **`pretrained/*/corpus.txt`** — corpora originais usados para indexação no app
- **`docs/demo.mp4`** — vídeo final do README (após Fase 6)
- **`gradle/wrapper/gradle-wrapper.jar`** — único `.jar` que deve estar no repo

Esses são casos onde o "artefato gerado" é parte essencial do produto distribuído. O `.gitignore` acima já os preserva implicitamente (não os menciona em regras de exclusão), mas vale documentar.

#### Convenção

Se durante o desenvolvimento aparecer um novo tipo de artefato gerado (cache, log, dump de profiling, output do Ktor em modo dev), **adicionar ao `.gitignore` no mesmo commit** que introduz a feature que o gera. Esquecer e commitar binários grandes em uma aplicação web é especialmente doloroso de reverter — uploads ou caches podem inflar rapidamente o histórico.

#### Validação na Fase 0

Antes de qualquer commit grande na Fase 0, rodar:

```bash
git status --ignored
```

Conferir que os diretórios `build/`, `.gradle/`, `.idea/` aparecem como ignorados. Se algum estiver sendo tracked por engano, remover com `git rm -r --cached <path>`.

---

## 5. Plano de Implementação em Fases

### Fase 0 — Setup + infraestrutura (estimativa: 3-4h)

**Objetivo:** Projeto compilando, infra completa, Tessera+Mosaic importados, Docker funcional.

#### Build e dependências

- [ ] `gradle init` com Kotlin DSL, plugin `application`
- [ ] `build.gradle.kts` com Ktor + Kotest + ktlint + detekt + JitPack
- [ ] Adicionar Tessera v0.0.7 e Mosaic v0.0.4 como `implementation`
- [ ] `mainClass = "dev.nlpplayground.ApplicationKt"`
- [ ] `gradle.properties` com versão `0.1.0-SNAPSHOT`

#### Qualidade

- [ ] `.gitignore` **exatamente conforme seção 4.14** (não inventar variações), `.editorconfig`, `.dockerignore`
- [ ] `config/detekt/detekt.yml` (adaptado do Mosaic)
- [ ] ktlint via plugin

#### Docker

- [ ] `Dockerfile` multi-stage funcional (seção 4.8)
- [ ] `docker-compose.yml` simples
- [ ] `docker build .` roda sem erro
- [ ] `docker run -p 8080:8080 ...` sobe e responde algo

#### Infraestrutura GitHub

- [ ] `.github/workflows/ci.yml` (seção 4.9)
- [ ] `.github/workflows/release.yml` (seção 4.10, foco em Docker)
- [ ] `.github/dependabot.yml` (seção 4.11)
- [ ] `.github/pull_request_template.md`

#### Documentação básica

- [ ] README inicial com "WIP", referenciando Tessera + Mosaic
- [ ] `CHANGELOG.md`, `LICENSE`

#### Validação

- [ ] App Ktor mínimo (só `/health` retornando "ok") sobe localmente
- [ ] Build Docker funciona
- [ ] `docker run` + curl `/health` = 200 OK
- [ ] CI verde no primeiro PR

**Critério de saída:** Imagem Docker construída localmente, app responde `/health`, CI verde, push pra main não dispara release prematuro.

### Fase 1 — Pipeline backend (estimativa: 3-4h)

**Objetivo:** Tessera + Mosaic operando dentro do app, sem UI ainda.

- [ ] `pipeline/PretrainedLoader.kt` carrega modelos do classpath
- [ ] `pipeline/CorpusTrainer.kt` treina BPE + cria embedding em memória
- [ ] `pipeline/SemanticSearch.kt` implementa busca top-K (cosine sobre mean-pooled)
- [ ] `pipeline/Pipeline.kt` data class agregando tokenizer + embeddings + sentences
- [ ] `session/SessionStore.kt` com cache em memória + eviction
- [ ] Teste unitário com corpus pequeno hard-coded validando: tokenize → embed → search retorna algo razoável

**Critério de saída:** Pipeline service testado por testes unitários. Dado corpus + query, retorna top-K em < 500ms (corpus de 200 frases).

### Fase 2 — Endpoints HTTP (estimativa: 2-3h)

**Objetivo:** API JSON funcional.

- [ ] `routes/ApiRoute.kt` com endpoints da seção 4.4
- [ ] Serialização JSON via `kotlinx.serialization` (já é dependência do Mosaic)
- [ ] Validação básica de input (tamanho de upload, query não vazia)
- [ ] Status codes apropriados (200, 400, 404, 500)
- [ ] Teste de integração via `TestApplication` do Ktor para cada endpoint

**Critério de saída:** Todos os endpoints testados retornam respostas válidas. cURL pra cada endpoint produz output esperado.

### Fase 3 — Frontend e templates (estimativa: 3-4h)

**Objetivo:** UI funcional (visualmente simples, mas operacional).

#### Home page (`/`)

- [ ] Hero + descrição + link pros 3 repos
- [ ] Duas opções: "Use pre-trained model" (lista 3 corpora) ou "Upload corpus" (form file)
- [ ] Disclaimer da seção 4.13 em destaque

#### Tela de exploração (`/explore/{sessionId}`)

- [ ] Layout da seção 4.12 com 3 tabs (Search, Tokenize, Compare)
- [ ] JS vanilla fazendo fetch nos endpoints `/api/*`
- [ ] Render dos resultados (top-K, tokens com chips coloridos, scores)
- [ ] Loading states durante calls
- [ ] Tratamento de erro UX (toast ou banner)

#### Upload com progress

- [ ] POST `/upload` retorna session ID imediatamente, treino roda async
- [ ] Cliente faz polling em `/api/status/{sessionId}` até ficar `ready`
- [ ] Mostra progress bar / spinner durante treino

**Critério de saída:** UI navegável, todos os 3 modos funcionando manualmente no browser.

### Fase 4 — Corpora pré-treinados (estimativa: 2-3h)

**Objetivo:** 3 corpora prontos no repo, carregando rápido.

- [ ] Baixar Alice no País das Maravilhas (Project Gutenberg) → `pretrained/alice-in-wonderland/corpus.txt`
- [ ] Baixar Shakespeare Sonnets → `pretrained/shakespeare-sonnets/corpus.txt`
- [ ] Extrair KDocs da stdlib do Kotlin (script ou manual) → `pretrained/kotlin-stdlib-docs/corpus.txt`
- [ ] Script `scripts/pretrain.kts` que carrega cada corpus, treina Tessera (2000 merges), cria Mosaic 128-dim, salva nos respectivos paths
- [ ] Rodar script, commitar `tessera.json` + `mosaic.bin` + `mosaic.bin.meta.json` de cada
- [ ] Atualizar `PretrainedLoader` pra listar os 3 nomes hard-coded (ou descobrir via filesystem do classpath)
- [ ] Validar tamanho final do JAR (< 30 MB)

**Critério de saída:** Em `/`, escolher qualquer corpus pré-treinado leva à tela de exploração em < 1 segundo (cold start do JAR pode somar 1-2s na primeira vez).

### Fase 5 — Polish visual e UX (estimativa: 2-3h)

**Objetivo:** Visual coerente e profissional.

- [ ] Definir paleta final (sugestão: preto + branco + cinza + acento roxo OU laranja)
- [ ] CSS limpo (variáveis CSS, sem framework)
- [ ] Fonts: Inter + JetBrains Mono via Google Fonts (consistência com Tessera/Mosaic)
- [ ] Responsividade básica (desktop first, mobile não quebrar)
- [ ] Favicon próprio
- [ ] Animações sutis (transitions, scroll fade-in)
- [ ] Estados visuais claros (loading, error, success)
- [ ] Headers/footers com cross-links pros 3 projetos

**Critério de saída:** UI presentável o suficiente pra gravar vídeo sem vergonha.

### Fase 6 — Documentação e vídeo (estimativa: 2-3h)

**Objetivo:** README pronto pra portfolio, vídeo gravado.

#### README

- [ ] Seção de header com badges (CI status, Docker pull, version, license)
- [ ] **Vídeo embarcado** no topo (markdown `<video>` ou GIF gerado do vídeo)
- [ ] Seção "What is this?" — 2 parágrafos
- [ ] Seção "Quick start" — `docker run` em 1 linha
- [ ] Seção "The Pipeline" — diagrama Tessera → Mosaic → busca
- [ ] Seção "Limitations" — disclaimer da seção 4.13
- [ ] Seção "The bigger picture" — link e descrição curta de Tessera e Mosaic, com badges
- [ ] Seção "Run locally" — `./gradlew run`
- [ ] Seção "Architecture" — link pra ARCHITECTURE.md

#### Vídeo (responsabilidade do usuário)

- [ ] Gravar screencast (sugiro Loom, OBS, ou QuickTime + iMovie)
- [ ] **Roteiro sugerido** (60-90s):
  - 0-10s: "Hello, I'm Hector. This is nlp-kotlin-playground, a demo of a Kotlin NLP pipeline I built from scratch."
  - 10-25s: Mostrar landing page, escolher corpus pré-treinado
  - 25-45s: Buscar uma frase, mostrar resultados, mencionar disclaimer
  - 45-60s: Mostrar aba Tokenize, ID dos tokens, "this is Tessera"
  - 60-75s: Aba Compare, "this is Mosaic computing cosine similarity"
  - 75-90s: "Code is on GitHub, including the two libraries that power this — Tessera and Mosaic. Thanks for watching!"
- [ ] Editar pra ficar limpo (cortar pausas, adicionar legendas opcionais)
- [ ] Salvar como `docs/demo.mp4`
- [ ] Embarcar no README

#### ARCHITECTURE.md

- [ ] Diagrama (Mermaid ou SVG) do fluxo HTTP → Pipeline → Tessera + Mosaic
- [ ] Decisões: por que Ktor, por que em memória, por que pré-treinados commitados, por que Docker

**Critério de saída:** README completo. Vídeo gravado, editado, embarcado. ARCHITECTURE.md publicado.

### Fase 7 — Release (estimativa: 1h)

**Objetivo:** Imagem Docker pública, tag v0.0.1.

- [ ] Merge final em main com commit `feat:` que dispara release
- [ ] Verificar que workflow:
  1. Calculou v0.0.1
  2. Atualizou versão em arquivos
  3. Buildou imagem Docker
  4. Publicou em `ghcr.io/hectorifc/nlp-kotlin-playground:v0.0.1` e `:latest`
  5. Criou git tag
  6. Criou GitHub Release
- [ ] Testar `docker pull ghcr.io/hectorifc/nlp-kotlin-playground:latest` em máquina limpa
- [ ] Atualizar README com badge de versão final

**Critério de saída:** Qualquer pessoa consegue rodar `docker run -p 8080:8080 ghcr.io/hectorifc/nlp-kotlin-playground:latest` e usar a aplicação.

---

## 6. Armadilhas Conhecidas (LER ANTES DE CODAR)

### 6.1. Mean pooling sobre vetores aleatórios = ruído

Como Mosaic é uma lookup table não treinada, `encodeMeanPooled(frase)` retorna um vetor que é, essencialmente, a soma de N vetores aleatórios dividida por N. Pela lei dos grandes números, mean pooling sobre muitos tokens tende a um vetor próximo de zero (média de uniforme em [-0.5/dim, +0.5/dim] é zero).

**Consequência:** frases longas vão ter scores de similaridade muito parecidos entre si. Frases curtas vão ter mais variação.

**Mitigação:** isso É parte da limitação documentada. O playground demonstra o pipeline, não a qualidade. Disclaimer cobre isso. Adicionalmente, ao escolher corpus pré-treinado de tamanho razoável (frases entre 5 e 30 tokens), o efeito é menos pronunciado.

### 6.2. Upload de corpus tem que ser limitado

Tessera treinando BPE com 2000 merges num corpus de 10MB pode levar **minutos**. Usuário fecha aba antes.

**Mitigação:**
- Limite hard de upload: 2 MB
- Mensagem clara antes do upload: "this will take up to 60 seconds"
- Treino async com polling de status
- Para corpus > 500KB, considerar reduzir `numMerges` proporcionalmente

### 6.3. SessionStore vaza memória se não tiver eviction

Cada Pipeline guarda tokenizer (~100KB) + embeddings (vocab × 128 × 4 bytes, pode ser ~3MB) + sentences. Se 100 pessoas fizerem upload, 300MB+ em memória.

**Mitigação:**
- Eviction obrigatória (cron interno do Ktor, executando a cada 10 min, removendo sessões > 1h)
- Limite máximo de sessões ativas (ex: 50). Quando exceder, evicta as mais antigas mesmo se < 1h.

### 6.4. Tessera pode quebrar em UTF-8 estranho no upload

Usuário sobe arquivo com encoding ruim (Latin-1, UTF-16, BOM, etc) e Tessera explode.

**Mitigação:**
- Tentar ler como UTF-8 strict; em caso de falha, retornar 400 com mensagem clara: "File must be UTF-8 encoded"
- Detectar BOM e remover antes de processar

### 6.5. Docker image inchada se incluir Gradle cache

Multi-stage build é crítico. Sem ele, imagem final inclui `/root/.gradle` e fica 1.5GB+.

**Mitigação:** Stage `builder` separado, copia só `build/install/...` pro stage final.

### 6.6. GHCR exige autenticação pra push

`docker push ghcr.io/...` precisa de `GITHUB_TOKEN` com `packages: write`. Workflow já configura isso, mas se for testar localmente, precisa `docker login ghcr.io -u USERNAME -p PAT`.

**Mitigação:** Documentar no README que `docker pull` é público (não exige auth), só push.

### 6.7. Healthcheck timing no Docker

App Ktor demora ~5-10s pra subir completamente. Se healthcheck começa em 1s, falha. Container fica em loop de restart.

**Mitigação:** `--start-period=10s` no healthcheck (já está no Dockerfile da seção 4.8).

### 6.8. Versão hardcoded em vários lugares

Igual nos outros projetos: versão aparece em `gradle.properties`, `README.md`, `Dockerfile` label. Workflow de release tem que atualizar todos via `sed`.

**Mitigação:** Listar todos os arquivos no workflow, exatamente como fizemos no PRD do Mosaic.

### 6.9. Frontend vanilla JS pode ficar bagunçado

Sem framework, é fácil acumular JS espaguete. Em 2 dias não compensa adicionar framework, mas é fácil deixar `app.js` virar 1000 linhas.

**Mitigação:** Separar em módulos (`app.js`, `search.js`, `tokenize.js`, `compare.js`) ou ao menos seções bem comentadas. Manter funções pequenas. Sem global state.

### 6.10. Corpora pré-treinados precisam de licença compatível

Alice in Wonderland e Shakespeare são domínio público (OK). Mas se um dia adicionar texto moderno (Wikipedia, blog posts), checar licença.

**Mitigação:** Lista atual (Alice, Shakespeare, KDocs Kotlin Apache 2.0) está coberta. Documentar atribuição no README.

### 6.11. Cuidado com path traversal no upload

Se aceitar `filename` do upload e usar diretamente em `File(name)`, attacker pode passar `../../../etc/passwd`.

**Mitigação:** Nunca usar filename do upload diretamente. Gerar UUID interno, ignorar nome original.

### 6.12. Vídeo no GitHub README tem limite de tamanho

GitHub aceita vídeo embarcado mas há limite de 100MB por arquivo no repo (ou 10MB pra renderizar no browser). Vídeo de 90s em 1080p facilmente passa de 30MB.

**Mitigação:**
- Comprimir vídeo: 720p, codec H.264, bitrate moderado
- FFmpeg sugerido: `ffmpeg -i raw.mov -vcodec libx264 -crf 28 -preset slow -vf "scale=1280:720" demo.mp4`
- Alternativa: upload no GitHub Release como asset (URL pública estável, sem inflar o repo) e linkar no README

### 6.13. CORS pode atrapalhar se separar front e back

Como tudo é servido pelo mesmo Ktor (frontend estático no `resources/static/`), CORS não é problema. Mas se um dia separar front e back, lembrar de habilitar CORS no Ktor.

**Mitigação:** Manter tudo no mesmo Ktor por agora.

---

## 7. Recursos e Referências

### 7.1. Referências primárias (REFERÊNCIA CANÔNICA)

- **Tessera** (projeto irmão): `https://github.com/HectorIFC/tessera` ou `/Users/hectorcardoso/tessera`
- **Mosaic** (projeto irmão): `https://github.com/HectorIFC/mosaic` ou `/Users/hectorcardoso/mosaic`

Esses dois são referência canônica para:
- Estrutura de workflows (`ci.yml`, `release.yml`)
- Configuração do dependabot e PR template
- Configuração de detekt e ktlint
- Estilo de README e ARCHITECTURE.md
- Versionamento via Conventional Commits

**Adaptar para este projeto**: substituir `JAR Maven publish` por `Docker GHCR publish` no workflow de release. Tudo mais é cópia direta ou cópia com adaptação de nome.

### 7.2. Referências técnicas

- **Ktor docs**: https://ktor.io/docs/
- **Ktor TestApplication**: https://ktor.io/docs/server-testing.html
- **GHCR docs**: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry
- **Multi-stage Docker**: https://docs.docker.com/build/building/multi-stage/
- **Conventional Commits**: https://www.conventionalcommits.org/

### 7.3. Corpora (Fase 4)

- **Alice in Wonderland**: https://www.gutenberg.org/ebooks/11 (UTF-8 plain text)
- **Shakespeare Sonnets**: https://www.gutenberg.org/ebooks/1041
- **Kotlin stdlib docs**: extrair de `kotlin/libraries/stdlib/src` ou usar `dokka` output

---

## 8. Workflow com Claude Code

### 8.1. Como você (Claude Code) deve operar

1. **Leia este PRD inteiro antes de qualquer ação.**
2. Confirme entendimento — em particular:
   - É **aplicação web**, não biblioteca
   - Tessera e Mosaic são **dependências via JitPack**, não submódulos
   - **SEM CHAT** — decisão fechada, não re-debater
   - Frontend é **HTML/CSS/vanilla JS**, sem React/Vue/etc
   - Docker é **obrigatório**, não opcional
3. Tessera e Mosaic são **referências canônicas** para infra. Consulte `https://github.com/HectorIFC/tessera` e `https://github.com/HectorIFC/mosaic` antes de criar workflows, configs, README ou ARCHITECTURE.
4. Trabalhe **fase por fase**. Não pule fases.
5. Ao começar uma fase, mostre o plano específico antes de codar.
6. Commit frequente, mensagens em Conventional Commits.
7. Após cada fase, mostre status report e aguarde confirmação antes de prosseguir.
8. Se encontrar decisão não coberta pelo PRD, pergunte. Não invente.

### 8.2. Convenções de código

- Imutabilidade por padrão (`val`, não `var`)
- Funções pequenas (< 30 linhas idealmente)
- Nomes em inglês
- Comentários explicando **por que**, não **o que**
- KDoc em handlers de rota e services principais
- `require` para validação de input, `check` para invariantes

### 8.3. Convenções de git

Conventional Commits com escopo:
- `feat(pipeline): wire Tessera + Mosaic in PipelineService`
- `feat(routes): add search endpoint`
- `feat(ui): build explore page with 3 tabs`
- `feat(docker): add multi-stage Dockerfile`
- `feat(pretrained): add Alice in Wonderland corpus`
- `fix(upload): reject non-UTF-8 files cleanly`
- `docs: add video to README`
- `build: configure GHCR publish in release workflow`
- `test(routes): cover search endpoint edge cases`

### 8.4. Versionamento

- `0.1.0-SNAPSHOT` → Fase 0
- `0.x.y` → Fases 1-6 (instável, sem release oficial)
- `0.0.1` → Fim da Fase 7 (primeira release pública via Docker)

---

## 9. Comunicação e Bloqueios

### 9.1. Quando perguntar ao usuário

- Decisões fora do escopo do PRD (especialmente: novas features tipo "vamos adicionar X?")
- Trade-offs de design no frontend (UX é subjetivo)
- Detalhes da paleta visual final (Fase 5)
- Antes de gravar o vídeo (Fase 6): user é quem grava
- Final de cada fase

### 9.2. Quando NÃO perguntar

- Detalhes de implementação cobertos pelo PRD
- Escolha de nomes de variáveis locais, helpers privados
- Quais testes adicionar — adicione todos que fizerem sentido

### 9.3. Status report ideal ao final de cada fase

```
✅ Fase X concluída.

Implementado:
- item 1
- item 2

Testes adicionados:
- N testes, todos passando

Critérios de saída:
- [x] critério A
- [x] critério B

Próximos passos: iniciando Fase Y. Posso prosseguir?
```

---

## 10. Apêndice — Glossário

- **Tessera:** projeto irmão, tokenizer BPE byte-level (v0.0.7)
- **Mosaic:** projeto irmão, embeddings lookup table (v0.0.4)
- **Pipeline:** instância em memória de (tokenizer + embeddings + sentences) usada por uma sessão
- **Session:** estado efêmero por usuário, identificado por UUID, vive em memória do JVM, evictado após 1h
- **Mean pooling:** combinar múltiplos vetores tirando média elemento a elemento, produzindo vetor único representando uma frase
- **Cosine similarity:** medida de similaridade entre vetores no range [-1, 1], independente de magnitude
- **GHCR:** GitHub Container Registry — onde a imagem Docker será publicada
- **Cold start:** primeira request após boot do JVM, tipicamente 1-2s mais lenta por classloading e JIT

---

## 11. Checklist mestre

- [ ] Fase 0: Setup + infra + Docker mínimo + CI verde
- [ ] Fase 1: Pipeline backend (Tessera + Mosaic operando)
- [ ] Fase 2: Endpoints HTTP testados
- [ ] Fase 3: Frontend funcional (3 tabs)
- [ ] Fase 4: 3 corpora pré-treinados commitados
- [ ] Fase 5: Visual polido
- [ ] Fase 6: README com vídeo + ARCHITECTURE.md
- [ ] Fase 7: Release v0.0.1 + Docker image pública no GHCR
- [ ] Todos os critérios da seção 3 atingidos
- [ ] Cross-links com Tessera e Mosaic em todos os READMEs (e vice-versa idealmente)

**Quando esse checklist estiver completo, o ecossistema NLP em Kotlin puro está completo e demonstrável: 3 repositórios, 1 vídeo, 1 comando Docker. Pronto pra portfolio.**

---

## 📜 Sobre o nome

> *"nlp-kotlin-playground"* — descritivo, direto, sem mistério. Quem busca "kotlin nlp" encontra. Quem busca "playground" entende a intenção.
>
> Não toda decisão precisa de uma metáfora bonita — às vezes o nome técnico bem escolhido faz mais por discoverability do que qualquer poética.

---

## 12. Considerações para o futuro (post-v0.0.1)

- **Treino de embeddings** (Word2Vec ou similar) — projeto separado, transformaria o disclaimer atual em "trained on Project Gutenberg" ou similar
- **Modelo de linguagem** (mini-transformer) — habilitaria geração de texto / chat de verdade
- **Hosting em VPS** — para deploy permanente com link público (vs apenas Docker pra rodar local)
- **Análise de mais corpora** — adicionar corpus em português, código fonte, etc.
- **Integração com tracing/observability** — overkill agora, útil se virar produto sério

Mas tudo isso é depois. Foco no presente: v0.0.1, playground funcional, vídeo gravado, ecossistema completo pra portfolio.
