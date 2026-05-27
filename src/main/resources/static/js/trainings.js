import { getJson } from "/js/api.js";

const REFRESH_MS = 3000;
const STUCK_WARN_MS = 5 * 60 * 1000;
const TERMINAL = new Set(["ready", "failed", "expired"]);

document.addEventListener("DOMContentLoaded", () => {
    wireFilterChanges();
    refreshLoop();
});

let lastRows = [];
let expandedId = null;

function wireFilterChanges() {
    document.querySelectorAll(".filters input").forEach((el) => {
        el.addEventListener("change", () => render(lastRows));
    });
}

async function refreshLoop() {
    const status = document.getElementById("trainings-status");
    while (true) {
        try {
            const response = await getJson("/api/trainings?limit=200");
            lastRows = response.items || [];
            status.className = "status-line";
            status.textContent = "";
            render(lastRows);
        } catch (e) {
            status.className = "status-line error";
            status.textContent = `Could not fetch trainings: ${e.message}`;
        }
        await sleep(REFRESH_MS);
    }
}

function activeFilters() {
    const statuses = Array.from(document.querySelectorAll("input[name=status]:checked")).map((c) => c.value);
    const since = document.querySelector("input[name=since]:checked")?.value || "all";
    return { statuses: new Set(statuses), since };
}

function sinceCutoff(since) {
    if (since === "hour") return Date.now() - 60 * 60 * 1000;
    if (since === "day") return Date.now() - 24 * 60 * 60 * 1000;
    return 0;
}

function render(rows) {
    const filters = activeFilters();
    const cutoff = sinceCutoff(filters.since);
    const filtered = rows.filter((r) => {
        if (filters.statuses.size > 0 && !filters.statuses.has(r.status)) return false;
        if (r.createdAt < cutoff) return false;
        return true;
    });
    document.getElementById("trainings-count").textContent =
        `${filtered.length} training${filtered.length === 1 ? "" : "s"}`;

    const body = document.getElementById("trainings-body");
    if (filtered.length === 0) {
        body.innerHTML = '<tr class="trainings-empty"><td colspan="6" class="muted">No trainings match these filters.</td></tr>';
        return;
    }

    body.innerHTML = "";
    for (const item of filtered) {
        body.appendChild(rowFor(item));
        if (expandedId === item.id) body.appendChild(detailRowFor(item));
    }
}

function rowFor(item) {
    const tr = document.createElement("tr");
    tr.className = "trainings-row";
    tr.dataset.id = item.id;

    const idCell = document.createElement("td");
    idCell.className = "col-id";
    const shortId = document.createElement("code");
    shortId.className = "mono-id";
    shortId.textContent = item.id.slice(0, 8);
    idCell.appendChild(shortId);

    const statusCell = document.createElement("td");
    statusCell.className = "col-status";
    statusCell.appendChild(badgeFor(item));

    const nameCell = document.createElement("td");
    nameCell.className = "col-name";
    nameCell.textContent = item.corpusFilename || "(no name)";

    const createdCell = document.createElement("td");
    createdCell.className = "col-created muted";
    createdCell.textContent = ago(item.createdAt);

    const updatedCell = document.createElement("td");
    updatedCell.className = "col-updated muted";
    updatedCell.textContent = ago(item.updatedAt);

    const actionsCell = document.createElement("td");
    actionsCell.className = "col-actions";
    if (item.status === "ready") {
        const exploreLink = document.createElement("a");
        exploreLink.href = `/explore/${item.id}`;
        exploreLink.textContent = "Explore";
        exploreLink.className = "action-link";
        actionsCell.appendChild(exploreLink);
    }
    const detailsBtn = document.createElement("button");
    detailsBtn.type = "button";
    detailsBtn.className = "action-link";
    detailsBtn.textContent = expandedId === item.id ? "Hide" : "Details";
    detailsBtn.addEventListener("click", () => {
        expandedId = expandedId === item.id ? null : item.id;
        render(lastRows);
    });
    actionsCell.appendChild(detailsBtn);

    tr.append(idCell, statusCell, nameCell, createdCell, updatedCell, actionsCell);
    return tr;
}

function detailRowFor(item) {
    const tr = document.createElement("tr");
    tr.className = "trainings-detail";
    const td = document.createElement("td");
    td.colSpan = 6;
    td.textContent = "Loading timeline…";
    tr.appendChild(td);
    // Lazy-fetch the timeline for the expanded row.
    getJson(`/api/training/${item.id}`)
        .then((detail) => {
            td.innerHTML = "";
            const list = document.createElement("ol");
            list.className = "event-timeline";
            for (const e of detail.events) {
                const li = document.createElement("li");
                const from = e.fromStatus ? `${e.fromStatus} → ` : "";
                li.innerHTML = `
                    <span class="event-when">${ago(e.occurredAt)}</span>
                    <span class="event-arrow">${from}<strong>${e.toStatus}</strong></span>
                    <span class="event-detail muted">${e.detail ? escapeHtml(e.detail) : ""}</span>
                `;
                list.appendChild(li);
            }
            td.appendChild(list);
            if (detail.errorMessage) {
                const err = document.createElement("p");
                err.className = "error-banner";
                err.textContent = detail.errorMessage;
                td.appendChild(err);
            }
        })
        .catch((e) => {
            td.textContent = `Could not load timeline: ${e.message}`;
        });
    return tr;
}

function badgeFor(item) {
    const span = document.createElement("span");
    const group = badgeGroup(item.status);
    span.className = `status-badge status-${group}`;
    span.textContent = item.status;
    if (!TERMINAL.has(item.status) && Date.now() - item.updatedAt > STUCK_WARN_MS) {
        span.title = `In ${item.status} for ${ago(item.updatedAt)} — potentially stuck`;
        span.classList.add("status-stuck");
    }
    return span;
}

function badgeGroup(status) {
    switch (status) {
        case "queued": return "queued";
        case "ready": return "ready";
        case "failed": return "failed";
        case "expired": return "expired";
        default: return "in-progress";
    }
}

function ago(epochMs) {
    const diff = Date.now() - epochMs;
    if (diff < 0) return "just now";
    const secs = Math.floor(diff / 1000);
    if (secs < 60) return `${secs}s ago`;
    const mins = Math.floor(secs / 60);
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    const days = Math.floor(hrs / 24);
    return `${days}d ago`;
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

function sleep(ms) {
    return new Promise((r) => setTimeout(r, ms));
}
