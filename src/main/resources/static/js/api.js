// Thin fetch wrapper used by every JS module. Centralizes JSON handling and
// error surfacing so the page-level scripts can stay focused on UI logic.

export async function getJson(path) {
    const response = await fetch(path, { headers: { Accept: "application/json" } });
    return parseOrThrow(response);
}

export async function postJson(path, body) {
    const response = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(body),
    });
    return parseOrThrow(response);
}

export async function postEmpty(path) {
    const response = await fetch(path, {
        method: "POST",
        headers: { Accept: "application/json" },
    });
    return parseOrThrow(response);
}

export async function postFile(path, file) {
    const form = new FormData();
    form.append("file", file, "corpus.txt");
    const response = await fetch(path, { method: "POST", body: form });
    return parseOrThrow(response);
}

async function parseOrThrow(response) {
    const isJson = (response.headers.get("Content-Type") || "").includes("application/json");
    const payload = isJson ? await response.json() : { error: await response.text() };
    if (!response.ok) {
        const err = new Error(payload.error || response.statusText);
        err.status = response.status;
        err.detail = payload.detail;
        throw err;
    }
    return payload;
}
