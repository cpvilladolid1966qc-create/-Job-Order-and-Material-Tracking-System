(() => {
    const form = document.getElementById("staff-invitation-form");
    if (!form || !window.StyleMariaApi) return;

    const token = new URLSearchParams(window.location.search).get("token") || "";
    const password = document.getElementById("invitation-password");
    const confirmation = document.getElementById("invitation-password-confirm");
    const feedback = document.getElementById("staff-invitation-feedback");
    const loginLink = document.getElementById("staff-login-link");
    const submit = document.getElementById("accept-invitation");

    const showMessage = (message, isError = false) => {
        feedback.textContent = message;
        feedback.classList.toggle("is-error", isError);
        feedback.classList.toggle("is-success", !isError);
    };

    if (!/^[A-Za-z0-9_-]{20,100}$/.test(token)) {
        submit.disabled = true;
        showMessage("This invitation link is invalid or incomplete. Ask an administrator to send a new invitation.", true);
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        if (password.value !== confirmation.value) {
            confirmation.setCustomValidity("Passwords do not match.");
            confirmation.reportValidity();
            return;
        }
        confirmation.setCustomValidity("");
        submit.disabled = true;
        try {
            const accepted = await window.StyleMariaApi.request("/api/v1/staff/invitations/accept", {
                method: "POST",
                body: JSON.stringify({ token, newPassword: password.value })
            });
            history.replaceState(null, "", window.location.pathname);
            form.hidden = true;
            loginLink.hidden = false;
            showMessage(`Your staff account for ${accepted.email} is ready. Sign in with your email and new password.`);
        } catch (error) {
            showMessage(`Invitation could not be accepted: ${error.message}`, true);
        } finally {
            submit.disabled = false;
        }
    });
})();
