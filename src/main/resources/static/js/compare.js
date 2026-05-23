import { postJson } from "/js/api.js";

export function wireCompareTab(sessionId) {
    const form = document.getElementById("compare-form");
    const status = document.getElementById("compare-status");
    const result = document.getElementById("compare-result");
    const score = document.getElementById("compare-score");
    const bar = document.getElementById("compare-bar");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const textA = document.getElementById("compare-a").value;
        const textB = document.getElementById("compare-b").value;
        if (!textA.trim() || !textB.trim()) return;
        status.className = "status-line";
        status.textContent = "Computing…";
        result.hidden = true;

        try {
            const response = await postJson(`/api/similarity/${sessionId}`, { textA, textB });
            status.textContent = "";
            score.textContent = response.score.toFixed(4);
            bar.style.width = `${Math.max(0, Math.min(100, ((response.score + 1) / 2) * 100))}%`;
            result.hidden = false;
        } catch (e) {
            status.className = "status-line error";
            status.textContent = e.message;
        }
    });
}
