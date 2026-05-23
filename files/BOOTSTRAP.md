# Bootstrap Guide — nlp-kotlin-playground

Passos que **você executa na sua máquina** para começar o projeto, antes de abrir o Claude Code.

---

## Passo 1 — Criar o repositório no GitHub

### Opção A — Via web

1. Acesse https://github.com/new
2. Preencha:
   - **Repository name:** `nlp-kotlin-playground`
   - **Description:** `Interactive playground demonstrating the Tessera + Mosaic NLP pipeline in Kotlin. Docker-ready demo.`
   - **Visibility:** Public
   - **NÃO** marque "Add a README", `.gitignore`, ou licença
3. Clique em **Create repository**

### Opção B — Via GitHub CLI

```bash
gh repo create nlp-kotlin-playground \
  --public \
  --description "Interactive playground demonstrating the Tessera + Mosaic NLP pipeline in Kotlin. Docker-ready demo." \
  --clone
```

### Topics sugeridos

Após criar, em **Settings → Topics** (ou no painel direito → About → ⚙️):

```
kotlin nlp ktor docker tessera mosaic playground demo machine-learning jvm embeddings tokenizer educational portfolio bpe
```

---

## Passo 2 — Clonar localmente

```bash
cd ~/projects
git clone https://github.com/HectorIFC/nlp-kotlin-playground.git
cd nlp-kotlin-playground
```

---

## Passo 3 — Adicionar os arquivos iniciais

```bash
# Copie os 3 arquivos que o Claude (chat) gerou para a raiz do projeto:
cp /caminho/PRD.md ./PRD.md
cp /caminho/README.md ./README.md
# BOOTSTRAP fica como referência sua, fora do repo se preferir
```

Primeiro commit:

```bash
git add PRD.md README.md
git commit -m "docs: add initial PRD and README"
git push origin main
```

---

## Passo 4 — Abrir o Claude Code no projeto

Pré-requisito: Claude Code instalado (`npm install -g @anthropic-ai/claude-code`).

```bash
cd ~/projects/nlp-kotlin-playground
claude
```

---

## Passo 5 — Primeira mensagem ao Claude Code

Cole esta mensagem na primeira interação:

> Olá! Este é o projeto **nlp-kotlin-playground**, o terceiro do meu ecossistema de NLP em Kotlin puro. Os outros dois já estão completos:
>
> - **Tessera** (tokenizer BPE): `https://github.com/HectorIFC/tessera` v0.0.7 — também em `/Users/hectorcardoso/tessera`
> - **Mosaic** (embeddings lookup table): `https://github.com/HectorIFC/mosaic` v0.0.4 — também em `/Users/hectorcardoso/mosaic`
>
> Este projeto é uma **aplicação web Kotlin/Ktor** com **Docker**, demonstrando o pipeline Tessera + Mosaic em ação.
>
> Antes de qualquer ação:
>
> 1. Leia o arquivo `PRD.md` por completo. Ele contém escopo, decisões já tomadas (NÃO re-debater), plano de 8 fases (0-7), critérios de aceitação e armadilhas.
> 2. Leia também o `README.md` para contexto.
> 3. **Tessera e Mosaic são referências canônicas** para infra (workflows, configs, README, ARCHITECTURE). Consulte os repos quando tiver dúvida — adapte trocando nomes e ajustando para o fato de que aqui o release publica imagem Docker (não JAR).
> 4. Atente para os pontos NÃO-NEGOCIÁVEIS:
>    - É **aplicação**, não biblioteca
>    - Tessera e Mosaic são **dependências via JitPack**
>    - **SEM CHAT** — discutido extensivamente, decisão fechada
>    - Frontend é **HTML/CSS/vanilla JS** — sem React/Vue/etc
>    - Docker é **obrigatório** e publicado em **GHCR** (não Docker Hub)
>    - Honestidade técnica: o disclaimer sobre embeddings não-treinados é parte do produto
> 5. Quando terminar, mostre um **resumo do entendimento** e qual será a Fase 0. Aguarde confirmação antes de implementar.
>
> Siga as convenções do PRD (Conventional Commits com escopo, uma fase por vez, status report ao fim).

---

## Passo 6 — Durante o desenvolvimento

- **Uma fase por vez.** Confirme cada uma antes de prosseguir.
- **Teste o Docker localmente** após Fase 0 e após Fase 7.
- **Não pule a Fase 6** — o vídeo é o entregável mais importante pra portfolio.
- Se algo do PRD não fizer sentido na prática, ajuste e documente — PRD vivo.

---

## Estrutura esperada após Fase 0

```
nlp-kotlin-playground/
├── PRD.md
├── README.md
├── CHANGELOG.md
├── LICENSE
├── .gitignore
├── .editorconfig
├── .dockerignore
├── Dockerfile
├── docker-compose.yml
├── .github/
│   ├── dependabot.yml
│   ├── pull_request_template.md
│   └── workflows/
│       ├── ci.yml
│       └── release.yml
├── config/detekt/
│   └── detekt.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
├── gradlew
├── gradlew.bat
└── src/
    ├── main/
    │   ├── kotlin/dev/nlpplayground/
    │   │   └── Application.kt        # /health endpoint placeholder
    │   └── resources/
    │       └── application.conf
    └── test/
        └── kotlin/dev/nlpplayground/
            └── HealthRouteTest.kt
```

---

## Checklist antes de começar

- [ ] Repositório criado no GitHub (`HectorIFC/nlp-kotlin-playground`)
- [ ] Topics configurados
- [ ] Repositório clonado localmente
- [ ] `PRD.md` e `README.md` na raiz
- [ ] Primeiro commit feito e pushado
- [ ] Claude Code aberto no diretório
- [ ] Primeira mensagem enviada

Quando tudo marcado, está pronto para a Fase 0.

---

## Sobre o vídeo (Fase 6)

Esse é o entregável mais visível do portfolio. Pra ficar bom:

**Ferramentas sugeridas:**
- **Loom** (mais simples) — grava direto no browser, gera URL
- **OBS Studio** (mais controle) — salva MP4 local
- **QuickTime + iMovie** (Mac, gratuito)

**Roteiro de ~75-90s** (já no PRD, seção 5 Fase 6):
- 0-10s: apresentação
- 10-25s: landing page + escolher corpus
- 25-45s: busca semântica + mostrar disclaimer
- 45-60s: aba Tokenize ("isso é Tessera")
- 60-75s: aba Compare ("isso é Mosaic")
- 75-90s: fechamento + links

**Compressão obrigatória:**
```bash
ffmpeg -i raw.mov -vcodec libx264 -crf 28 -preset slow -vf "scale=1280:720" docs/demo.mp4
```

Manter abaixo de 10MB pra renderizar inline no GitHub.

Boa sorte! 🎬 Tessera + Mosaic + Playground = ecossistema completo pra portfolio.
