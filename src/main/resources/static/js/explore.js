import { getJson } from "/js/api.js";
import { wireSearchTab } from "/js/search.js";
import { wireTokenizeTab } from "/js/tokenize.js";
import { wireCompareTab } from "/js/compare.js";

document.addEventListener("DOMContentLoaded", () => {
    const sessionId = readSessionIdFromUrl();
    if (!sessionId) {
        document.body.innerHTML = "<p style=\"padding:24px\">Missing session id. <a href=\"/\">Go home</a>.</p>";
        return;
    }
    wireCorpusHeader(sessionId);
    wireTabs();
    wireSearchTab(sessionId);
    wireTokenizeTab(sessionId);
    wireCompareTab(sessionId);
});

function readSessionIdFromUrl() {
    // Pathname is /explore/<id>; split keeps the id even if a trailing slash sneaks in.
    const parts = window.location.pathname.split("/").filter(Boolean);
    return parts[1] || null;
}

async function wireCorpusHeader(sessionId) {
    try {
        const status = await getJson(`/api/status/${sessionId}`);
        document.getElementById("corpus-name").textContent = status.name || sessionId;
    } catch (e) {
        document.getElementById("corpus-name").textContent = "(unknown)";
    }
}

function wireTabs() {
    const tabs = document.querySelectorAll(".tab");
    const panels = document.querySelectorAll(".tab-panel");
    tabs.forEach((tab) => {
        tab.addEventListener("click", () => {
            tabs.forEach((t) => t.setAttribute("aria-selected", t === tab ? "true" : "false"));
            const targetId = `tab-${tab.dataset.tab}`;
            panels.forEach((panel) => {
                panel.hidden = panel.id !== targetId;
            });
        });
    });
}
