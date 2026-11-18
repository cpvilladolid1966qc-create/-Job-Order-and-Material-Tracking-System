(() => {
    const manageButton = document.getElementById("manage-staff-server");
    if (!manageButton || !window.StyleMariaApi) return;

    const api = window.StyleMariaApi;
    const dialog = document.getElementById("staff-dialog");
    const list = document.getElementById("staff-directory-list");
    const search = document.getElementById("staff-search");
    const statusFilter = document.getElementById("staff-status-filter");
    const editor = document.getElementById("staff-editor");
    const form = document.getElementById("staff-form");
    const feedback = document.getElementById("staff-manager-feedback");
    const staffName = document.getElementById("staff-name");
    const staffEmail = document.getElementById("staff-email");
    let staff = [];

    const setFeedback = (message, isError = false) => {
        feedback.textContent = message;
        feedback.classList.toggle("is-error", isError);
        feedback.classList.toggle("is-success", !isError && message.length > 0);
    };

    const renderStaff = () => {
        const query = search.value.trim().toLocaleLowerCase();
        const selectedStatus = statusFilter.value;
        const filtered = staff.filter((member) => {
            const active = Boolean(member.active);
            return `${member.name} ${member.email} Staff`.toLocaleLowerCase().includes(query)
                && (selectedStatus === "all" || (selectedStatus === "active" ? active : !active));
        });
        const activeCount = staff.filter((member) => member.active).length;
        document.getElementById("staff-total-count").textContent = String(staff.length);
        document.getElementById("staff-active-count").textContent = String(activeCount);
        document.getElementById("staff-inactive-count").textContent = String(staff.length - activeCount);
        list.replaceChildren();

        filtered.forEach((member) => {
            const row = document.createElement("li");
            row.className = "staff-directory-row";
            const avatar = document.createElement("span");
            avatar.className = "staff-directory-avatar";
            avatar.setAttribute("aria-hidden", "true");
            avatar.textContent = member.name.trim().charAt(0).toLocaleUpperCase();

            const details = document.createElement("span");
            details.className = "staff-directory-details";
            const name = document.createElement("strong");
            name.textContent = member.name;
            const email = document.createElement("span");
            email.textContent = member.email;
            const role = document.createElement("span");
            role.textContent = "Staff";
            details.append(name, email, role);

            const badge = document.createElement("span");
            badge.className = `staff-status-badge ${member.active ? "active" : "inactive"}`;
            badge.textContent = member.active ? "Active" : "Inactive";
            const action = document.createElement("button");
            action.type = "button";
            action.className = "dashboard-button dashboard-button-secondary";
            action.dataset.staffId = String(member.id);
            action.dataset.active = String(!member.active);
            action.textContent = member.active ? "Deactivate" : "Activate";
            row.append(avatar, details, badge, action);
            list.append(row);
        });
        document.getElementById("staff-empty-state").hidden = filtered.length > 0;

        const preview = document.querySelector(".staff-panel .staff-list");
        preview.replaceChildren();
        staff.slice(0, 3).forEach((member) => {
            const row = document.createElement("li");
            row.className = "staff-member";
            const avatar = document.createElement("span");
            avatar.className = "staff-avatar staff-preview-avatar";
            avatar.textContent = member.name.trim().charAt(0).toLocaleUpperCase();
            avatar.setAttribute("aria-hidden", "true");
            const details = document.createElement("span");
            details.className = "staff-details";
            const name = document.createElement("strong");
            name.textContent = member.name;
            const role = document.createElement("span");
            role.textContent = "Staff";
            details.append(name, role);
            const badge = document.createElement("span");
            badge.className = member.active ? "staff-active" : "staff-inactive";
            badge.textContent = member.active ? "Active" : "Inactive";
            row.append(avatar, details, badge);
            preview.append(row);
        });
        document.querySelector(".staff-total").textContent = `Total staff: ${staff.length}`;
    };

    const loadStaff = async () => {
        setFeedback("Loading staff accounts…");
        try {
            staff = await api.request("/api/v1/admin/staff");
            renderStaff();
            setFeedback("");
        } catch (error) {
            setFeedback(`Staff accounts could not be loaded: ${error.message}`, true);
        }
    };

    manageButton.addEventListener("click", async () => {
        dialog.showModal();
        await loadStaff();
    });
    document.getElementById("close-staff-dialog").addEventListener("click", () => dialog.close());
    dialog.addEventListener("click", (event) => {
        if (event.target === dialog) dialog.close();
    });
    document.getElementById("add-staff").addEventListener("click", () => {
        form.reset();
        editor.hidden = false;
        staffName.focus();
    });
    document.getElementById("cancel-staff-edit").addEventListener("click", () => {
        form.reset();
        editor.hidden = true;
    });
    [search, statusFilter].forEach((element) => {
        element.addEventListener(element === search ? "input" : "change", renderStaff);
    });

    list.addEventListener("click", async (event) => {
        const button = event.target.closest("button[data-staff-id]");
        if (!button) return;
        button.disabled = true;
        try {
            await api.request(`/api/v1/admin/staff/${encodeURIComponent(button.dataset.staffId)}`, {
                method: "PATCH",
                body: JSON.stringify({ active: button.dataset.active === "true" })
            });
            await loadStaff();
            setFeedback(button.dataset.active === "true"
                ? "Staff account activated."
                : "Staff account deactivated.");
        } catch (error) {
            setFeedback(`Staff status could not be changed: ${error.message}`, true);
        } finally {
            button.disabled = false;
        }
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const button = form.querySelector('[type="submit"]');
        button.disabled = true;
        try {
            const invitation = await api.request("/api/v1/admin/staff/invitations", {
                method: "POST",
                body: JSON.stringify({
                    name: staffName.value.trim(),
                    email: staffEmail.value.trim()
                })
            });
            form.reset();
            editor.hidden = true;
            setFeedback(`Invitation sent to ${invitation.email}. It expires ${new Date(invitation.expiresAt).toLocaleString()}.`);
        } catch (error) {
            setFeedback(`Invitation could not be sent: ${error.message}`, true);
        } finally {
            button.disabled = false;
        }
    });
})();
