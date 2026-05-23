import { postJson } from "/js/api.js";

export function wireSearchTab(sessionId) {
    const form = document.getElementById("search-form");
    const status = document.getElementById("search-status");
    const results = document.getElementById("search-results");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const query = document.getElementById("search-query").value.trim();
        const topK = parseInt(document.getElementById("search-topk").value, 10) || 5;
        if (!query) return;
        status.className = "status-line";
        status.textContent = "Searching…";
        results.innerHTML = "";

        try {
            const response = await postJson(`/api/search/${sessionId}`, { query, topK });
            status.textContent = `${response.results.length} result(s) for "${response.query}"`;
            renderResults(results, response.results);
        } catch (e) {
            status.className = "status-line error";
            status.textContent = e.message;
        }
    });
}

function renderResults(target, hits) {
    target.innerHTML = "";
    for (const hit of hits) {
        const li = document.createElement("li");

        const sentence = document.createElement("div");
        sentence.className = "result-sentence";
        sentence.textContent = hit.sentence;

        const row = document.createElement("div");
        row.className = "result-score-row";
        row.append(`score: ${hit.score.toFixed(4)}`);

        const bar = document.createElement("div");
        bar.className = "score-bar";
        const fill = document.createElement("div");
        fill.className = "score-bar-fill";
        // Map [-1, 1] to [0%, 100%]. Random embeddings rarely produce negatives,
        // but we keep the mapping symmetric to be honest about cosine semantics.
        fill.style.width = `${Math.max(0, Math.min(100, ((hit.score + 1) / 2) * 100))}%`;
        bar.appendChild(fill);
        row.appendChild(bar);

        li.appendChild(sentence);
        li.appendChild(row);
        target.appendChild(li);
    }
}
