(() => {
    const request = async (path, options = {}) => {
        const headers = new Headers(options.headers || {});
        if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }

        const response = await fetch(path, {
            credentials: "same-origin",
            ...options,
            headers
        });
        const text = await response.text();
        let payload = null;
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch {
                payload = text;
            }
        }
        if (!response.ok) {
            const message = payload && typeof payload === "object"
                ? (payload.message || payload.error || `Request failed (${response.status}).`)
                : (payload || `Request failed (${response.status}).`);
            const error = new Error(message);
            error.status = response.status;
            error.code = payload && typeof payload === "object" ? payload.error : null;
            throw error;
        }
        return payload;
    };

    window.StyleMariaApi = { request };
})();
