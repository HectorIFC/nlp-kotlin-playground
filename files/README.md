# nlp-kotlin-playground

> An interactive playground demonstrating the **Tessera + Mosaic** NLP pipeline in pure Kotlin.

## Status

🚧 **In development** — Phase 0 (Setup + infrastructure)

See [PRD.md](./PRD.md) for the full specification.

## About

This is the third project in a small ecosystem of pure-Kotlin NLP libraries:

| Project | Role | Status |
|---------|------|--------|
| [Tessera](https://github.com/HectorIFC/tessera) | Byte-level BPE tokenizer | ✅ v0.0.7 |
| [Mosaic](https://github.com/HectorIFC/mosaic) | Lookup-based token embeddings | ✅ v0.0.4 |
| **nlp-kotlin-playground** | Interactive web demo of the pipeline | 🚧 in progress |

The playground exists for one purpose: **to demonstrate that the libraries work, end to end, in a tangible application** — searchable, tokenizable, and inspectable in the browser. It is the part where a recruiter or curious developer can click a link, run a Docker command, and *see* the pipeline in action without ever opening a Kotlin file.

### What it does

1. Pick a pre-trained corpus (Alice in Wonderland, Shakespeare, Kotlin stdlib docs) **or upload your own**
2. Watch the pipeline run: Tessera tokenizes, Mosaic creates embeddings
3. Explore the result:
   - **Semantic search**: type a phrase, see top-K most similar sentences from the corpus
   - **Tokenize**: see tokens and IDs for any input text
   - **Compare**: pick two pieces of text, see their cosine similarity score

### Honest limitation

Mosaic provides embeddings as a **lookup table** — the vectors are randomly initialized, not trained. The pipeline (tokenize → vector lookup → mean pooling → cosine similarity) is real and identical to production-grade pipelines, but **without training, similarities reflect random structure**, not semantic meaning. Training (Word2Vec, GloVe, etc.) is a future, separate project.

This is intentional. The playground showcases the *plumbing*, which is the harder part to build well in pure Kotlin without ML frameworks.

## Quick start

### Docker (after v0.0.1 release)

```bash
docker run -p 8080:8080 ghcr.io/hectorifc/nlp-kotlin-playground:latest
```

Open http://localhost:8080.

### Local development

```bash
git clone https://github.com/HectorIFC/nlp-kotlin-playground.git
cd nlp-kotlin-playground
./gradlew run
```

## Watch it in action

🎬 A 60-90s demo video will be embedded here after Phase 6.

## Architecture

```
Browser
   │ HTTP
   ▼
Ktor (Kotlin/JVM)
   │
   ├── PretrainedLoader  ───┐
   │                        │
   ├── CorpusTrainer  ──────┤
   │                        ▼
   ├── PipelineService  ── Tessera (tokenization)
   │                        +
   ├── SessionStore        Mosaic (embeddings)
   │                        +
   └── SemanticSearch    Cosine similarity
```

See [ARCHITECTURE.md](./ARCHITECTURE.md) (created in Phase 6) for details.

## Roadmap

- [x] Define scope and architecture (see PRD.md)
- [ ] **Phase 0**: Setup Kotlin/Ktor + Docker + CI/CD infrastructure
- [ ] **Phase 1**: Pipeline backend (Tessera + Mosaic wiring)
- [ ] **Phase 2**: HTTP endpoints
- [ ] **Phase 3**: Frontend with 3 tabs (Search, Tokenize, Compare)
- [ ] **Phase 4**: Pre-trained corpora (Alice, Shakespeare, Kotlin docs)
- [ ] **Phase 5**: Visual polish
- [ ] **Phase 6**: README with embedded demo video + ARCHITECTURE.md
- [ ] **Phase 7**: Release v0.0.1 + public Docker image on GHCR

## Related projects

- 🧩 [Tessera](https://github.com/HectorIFC/tessera) — the tokenizer this playground uses
- 🎨 [Mosaic](https://github.com/HectorIFC/mosaic) — the embedding library this playground uses

Both are 100% pure Kotlin, JVM-only, built from scratch as educational projects.

## License

MIT — see [LICENSE](./LICENSE).
