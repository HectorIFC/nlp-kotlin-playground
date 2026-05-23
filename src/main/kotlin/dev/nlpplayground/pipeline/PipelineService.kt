package dev.nlpplayground.pipeline

/**
 * Thin orchestrator that the HTTP layer talks to. Hides the choice between
 * "load a bundled pre-trained corpus" and "train a fresh BPE on uploaded text".
 *
 * The service is stateless — Pipelines live in `SessionStore`, not here.
 */
internal class PipelineService(private val loader: PretrainedLoader = PretrainedLoader.bundled()) {

    fun availablePretrained(): List<String> = loader.list()

    fun loadPretrained(name: String): Pipeline = loader.load(name)

    fun trainFromCorpus(name: String, corpus: String): Pipeline = CorpusTrainer.train(name, corpus)
}
