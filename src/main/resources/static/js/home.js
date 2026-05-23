import { getJson, postEmpty, postFile } from "/js/api.js";

const POLL_INTERVAL_MS = 1000;
const POLL_TIMEOUT_MS = 120_000;

document.addEventListener("DOMContentLoaded", () => {
    renderPretrainedList();
    wireUploadForm();
});

async function renderPretrainedList() {
    const list = document.getElementById("pretrained-list");
    const empty = document.getElementById("pretrained-empty");
    try {
        const { available } = await getJson("/pretrained");
        list.innerHTML = "";
        if (!available.length) {
            empty.hidden = false;
            return;
        }
        for (const name of available) {
            const li = document.createElement("li");
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = name;
            button.addEventListener("click", () => loadPretrained(name, button));
            li.appendChild(button);
            list.appendChild(li);
        }
    } catch (e) {
        list.innerHTML = `<li class="muted">Could not load pre-trained list: ${escapeHtml(e.message)}</li>`;
    }
}

async function loadPretrained(name, button) {
    button.disabled = true;
    const original = button.textContent;
    button.textContent = `Loading ${name}…`;
    try {
        const { sessionId } = await postEmpty(`/pretrained/${encodeURIComponent(name)}`);
        window.location.href = `/explore/${sessionId}`;
    } catch (e) {
        button.disabled = false;
        button.textContent = original;
        alert(`Could not load corpus '${name}': ${e.message}`);
    }
}

function wireUploadForm() {
    const form = document.getElementById("upload-form");
    const fileInput = document.getElementById("upload-file");
    const submit = document.getElementById("upload-submit");
    const status = document.getElementById("upload-status");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const file = fileInput.files[0];
        if (!file) return;
        submit.disabled = true;
        status.hidden = false;
        status.className = "upload-status";
        status.textContent = `Uploading ${file.name}…`;

        try {
            const { sessionId } = await postFile("/upload", file);
            status.textContent = "Training pipeline (this can take up to 60 seconds)…";
            const finalState = await pollUntilTerminal(sessionId);
            if (finalState.state === "ready") {
                status.className = "upload-status success";
                status.textContent = "Pipeline ready — opening explorer…";
                window.location.href = `/explore/${sessionId}`;
            } else {
                status.className = "upload-status error";
                status.textContent = `Training failed: ${finalState.error || "unknown error"}`;
                submit.disabled = false;
            }
        } catch (e) {
            status.className = "upload-status error";
            status.textContent = `${e.message}${e.detail ? ` — ${e.detail}` : ""}`;
            submit.disabled = false;
        }
    });
}

async function pollUntilTerminal(sessionId) {
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    while (Date.now() < deadline) {
        const status = await getJson(`/api/status/${sessionId}`);
        if (status.state === "ready" || status.state === "error") return status;
        await sleep(POLL_INTERVAL_MS);
    }
    throw new Error("Timed out waiting for the pipeline to become ready.");
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function escapeHtml(s) {
    return s.replace(/[&<>"']/g, (c) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;",
    })[c]);
}
