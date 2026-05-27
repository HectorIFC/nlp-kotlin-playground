import { getJson, postEmpty, postFile } from "/js/api.js";

// Mirrors the server-side limit in UploadRoute.kt . Enforcing it
// client-side saves the round trip and gives a friendlier error.
const MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

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
        const { trainingId } = await postEmpty(`/pretrained/${encodeURIComponent(name)}`);
        // Pre-trained corpora are persisted in the trainings table directly as
        // READY, so we can skip the progress page and jump to /explore.
        window.location.href = `/explore/${trainingId}`;
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

        if (file.size > MAX_UPLOAD_BYTES) {
            const mb = (file.size / 1024 / 1024).toFixed(2);
            status.hidden = false;
            status.className = "upload-status error";
            status.textContent = `File is ${mb} MB — the limit is 2 MB. Trim it and try again.`;
            return;
        }

        submit.disabled = true;
        status.hidden = false;
        status.className = "upload-status";
        status.textContent = `Uploading ${file.name}…`;

        try {
            const { trainingId } = await postFile("/upload", file);
            // The actual training happens in the consumer; the dedicated progress
            // page polls /api/training/{id} and redirects to /explore when READY.
            window.location.href = `/training/${trainingId}/progress`;
        } catch (e) {
            status.className = "upload-status error";
            status.textContent = `${e.message}${e.detail ? ` — ${e.detail}` : ""}`;
            submit.disabled = false;
        }
    });
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
