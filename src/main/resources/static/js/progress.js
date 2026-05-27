import { getJson } from "/js/api.js";

const POLL_INTERVAL_MS = 2000;
const ORDER = ["queued", "downloading", "tokenizing", "embedding", "indexing", "ready"];

document.addEventListener("DOMContentLoaded", () => {
    const trainingId = readTrainingIdFromUrl();
    if (!trainingId) {
        showError("Missing training id in the URL.");
        return;
    }
    document.getElementById("progress-id").textContent = trainingId.slice(0, 8);
    pollLoop(trainingId);
});

function readTrainingIdFromUrl() {
    // /training/<id>/progress — pick the second path segment.
    const parts = window.location.pathname.split("/").filter(Boolean);
    return parts[1] || null;
}

async function pollLoop(trainingId) {
    while (true) {
        let detail;
        try {
            detail = await getJson(`/api/training/${trainingId}`);
        } catch (e) {
            showError(`Could not reach the server: ${e.message}`);
            await sleep(POLL_INTERVAL_MS);
            continue;
        }
        // Don't repaint the timeline when we're already in a terminal state —
        // a `failed` poll would otherwise reset every step to pending and erase
        // the "we got this far" signal the user wants to see.
        if (detail.status === "ready") {
            updateTimeline(detail);
            // Small grace period so the "ready" tick visibly lights up before the redirect.
            await sleep(300);
            window.location.href = `/explore/${trainingId}`;
            return;
        }
        if (detail.status === "failed") {
            showError(detail.errorMessage || "Training failed (no error message reported).");
            return;
        }
        if (detail.status === "expired") {
            showError("Training expired before it could be loaded. Upload the corpus again.");
            return;
        }
        updateTimeline(detail);
        await sleep(POLL_INTERVAL_MS);
    }
}

function updateTimeline(detail) {
    const idx = ORDER.indexOf(detail.status);
    const filename = detail.corpusFilename;
    if (filename) {
        document.getElementById("progress-name").textContent = filename;
    }
    const steps = document.querySelectorAll(".timeline-step");
    steps.forEach((el) => {
        const state = el.dataset.state;
        const stateIdx = ORDER.indexOf(state);
        el.classList.remove("step-done", "step-active", "step-pending");
        if (idx < 0) {
            el.classList.add("step-pending");
        } else if (stateIdx < idx) {
            el.classList.add("step-done");
        } else if (stateIdx === idx) {
            el.classList.add(detail.status === "ready" ? "step-done" : "step-active");
        } else {
            el.classList.add("step-pending");
        }
    });
}

function showError(message) {
    const banner = document.getElementById("progress-error");
    banner.textContent = message;
    banner.hidden = false;
}

function sleep(ms) {
    return new Promise((r) => setTimeout(r, ms));
}
