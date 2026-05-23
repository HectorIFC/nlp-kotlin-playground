import { postJson } from "/js/api.js";

export function wireTokenizeTab(sessionId) {
    const form = document.getElementById("tokenize-form");
    const status = document.getElementById("tokenize-status");
    const target = document.getElementById("tokenize-tokens");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const text = document.getElementById("tokenize-text").value;
        if (!text.trim()) return;
        status.className = "status-line";
        status.textContent = "Tokenizing…";
        target.innerHTML = "";

        try {
            const response = await postJson(`/api/tokenize/${sessionId}`, { text });
            status.textContent = `${response.tokens.length} token(s) for "${response.text}"`;
            renderChips(target, response.tokens);
        } catch (e) {
            status.className = "status-line error";
            status.textContent = e.message;
        }
    });
}

function renderChips(target, tokens) {
    target.innerHTML = "";
    for (const tok of tokens) {
        const span = document.createElement("span");
        span.className = "chip";

        const text = document.createElement("span");
        text.textContent = displayText(tok.text);

        const id = document.createElement("span");
        id.className = "chip-id";
        id.textContent = `#${tok.id}`;

        span.appendChild(text);
        span.appendChild(id);
        target.appendChild(span);
    }
}

// BPE byte tokens can be mid-codepoint or whitespace-only; render visible glyphs
// so the user sees what landed in the vocabulary without copy/paste invisible chars.
function displayText(s) {
    if (s === "") return "∅";
    if (s === " ") return "·";
    if (s === "\n") return "↵";
    if (s === "\t") return "⇥";
    return s;
}
