(() => {
    const dialog = document.getElementById("all-orders-dialog");
    const tableBody = document.getElementById("all-orders-body");
    if (!dialog || !tableBody || !window.StyleMariaApi) return;

    const api = window.StyleMariaApi;
    const search = document.getElementById("all-orders-search");
    const statusFilter = document.getElementById("all-orders-status");
    const dateFilter = document.getElementById("all-orders-date");
    const emptyState = document.getElementById("all-orders-empty");
    const countLabel = document.getElementById("all-orders-count");
    const pageButtons = document.getElementById("orders-page-buttons");
    const dashboardBody = document.getElementById("orders-body");
    const dashboardEmpty = document.getElementById("orders-empty");
    const dashboardSearch = document.getElementById("order-search");
    const dashboardFilters = [...document.querySelectorAll("[data-order-status]")];
    const recentBody = document.querySelector(".recent-orders-panel .dashboard-data-table tbody");
    const recentEmpty = document.getElementById("recent-orders-empty");
    const inventoryBody = document.getElementById("dashboard-inventory-body");
    const inventoryEmpty = document.getElementById("dashboard-inventory-empty");
    const pageSize = 10;
    let requests = [];
    let requestsLoadError = "";
    let page = 1;
    let activeDashboardStatus = "all";
    const setMetric = (name, value, note) => {
        const valueElement = document.querySelector(`[data-dashboard-value="${name}"]`);
        const noteElement = document.querySelector(`[data-dashboard-note="${name}"]`);
        if (valueElement) valueElement.textContent = value;
        if (noteElement) noteElement.textContent = note;
    };
    const appendCell = (row, value) => {
        const cell = document.createElement("td");
        cell.textContent = value;
        row.append(cell);
    };
    const errorMessage = (reason) => reason instanceof Error ? reason.message : String(reason);
    const titleCase = (status) => String(status || "NEW_REQUEST")
        .replaceAll("_", " ")
        .toLocaleLowerCase()
        .replace(/\b\w/g, (letter) => letter.toLocaleUpperCase());

    const filteredRequests = () => {
        const query = search.value.trim().toLocaleLowerCase();
        const status = statusFilter.value;
        const date = dateFilter.value;
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        return requests.filter((request) => {
            const matchesSearch = `${request.requestId} ${request.customerName} ${request.referenceImageName || ""}`
                .toLocaleLowerCase().includes(query);
            const statusKey = titleCase(request.status).toLocaleLowerCase();
            const matchesStatus = status === "all" || statusKey === status;
            const submitted = new Date(request.submittedAt);
            const age = Math.floor((today - new Date(submitted.getFullYear(), submitted.getMonth(), submitted.getDate())) / 86400000);
            const matchesDate = date === "all"
                || (date === "today" ? age === 0 : age < Number(date));
            return matchesSearch && matchesStatus && matchesDate;
        });
    };

    const filteredDashboardRequests = () => {
        const query = dashboardSearch.value.trim().toLocaleLowerCase();
        return requests.filter((request) => {
            const matchesSearch = `${request.requestId} ${request.customerName} ${request.referenceImageName || ""}`
                .toLocaleLowerCase().includes(query);
            const matchesStatus = activeDashboardStatus === "all"
                || titleCase(request.status).toLocaleLowerCase() === activeDashboardStatus;
            return matchesSearch && matchesStatus;
        });
    };

    const reviewLink = (request) => {
        const link = document.createElement("a");
        link.className = "all-orders-view";
        link.href = `/custom-request-detail.html?request=${encodeURIComponent(request.requestId)}`;
        link.textContent = "Review";
        return link;
    };

    const renderDashboard = () => {
        const filtered = filteredDashboardRequests();
        setMetric("pending-requests", requestsLoadError ? "—" : String(requests.length),
            requestsLoadError ? `Unable to load requests: ${requestsLoadError}` : "Open custom requests");
        dashboardBody.replaceChildren();
        filtered.slice(0, 10).forEach((request) => {
            const row = document.createElement("tr");
            [
                request.requestId,
                request.customerName || "—",
                request.referenceImageName || "Custom mirror request",
                titleCase(request.status),
                new Date(request.submittedAt).toLocaleDateString()
            ].forEach((value, index) => {
                const cell = document.createElement("td");
                cell.textContent = value;
                if (index === 0) cell.className = "all-orders-id";
                row.append(cell);
            });
            const action = document.createElement("td");
            action.append(reviewLink(request));
            row.append(action);
            dashboardBody.append(row);
        });
        dashboardEmpty.hidden = filtered.length > 0;
        dashboardEmpty.textContent = requestsLoadError
            ? `Customer requests could not be loaded: ${requestsLoadError}`
            : requests.length === 0
                ? "No customer custom orders are currently in the database."
                : "No requests match the current filters.";

        recentBody.replaceChildren();
        requests.slice(0, 5).forEach((request) => {
            const row = document.createElement("tr");
            [
                request.requestId,
                request.customerName || "—",
                request.referenceImageName || "Custom mirror request",
                new Date(request.submittedAt).toLocaleDateString(),
                titleCase(request.status)
            ].forEach((value) => {
                const cell = document.createElement("td");
                cell.textContent = value;
                row.append(cell);
            });
            recentBody.append(row);
        });
        if (recentEmpty) {
            recentEmpty.hidden = requests.length > 0 && !requestsLoadError;
            recentEmpty.textContent = requestsLoadError
                ? `Customer requests could not be loaded: ${requestsLoadError}`
                : "No customer requests are currently in the database.";
        }
    };

    const renderInventory = (materials) => {
        const lowStockMaterials = materials.filter((material) => material.lowStock);
        const displayMaterials = [...materials]
            .sort((left, right) => Number(right.lowStock) - Number(left.lowStock)
                || left.name.localeCompare(right.name))
            .slice(0, 5);
        inventoryBody.replaceChildren();
        displayMaterials.forEach((material) => {
            const row = document.createElement("tr");
            appendCell(row, material.name);
            appendCell(row, material.lowStock ? "Low stock" : "In stock");
            appendCell(row, `${material.quantityOnHand} ${material.unitCode}`);
            inventoryBody.append(row);
        });
        inventoryEmpty.hidden = displayMaterials.length > 0;
        inventoryEmpty.textContent = materials.length === 0
            ? "No active inventory materials to display."
            : "No inventory data to display.";
        setMetric("stock-alerts", String(lowStockMaterials.length),
            `${lowStockMaterials.length} of ${materials.length} active materials at or below threshold`);
    };

    const loadOperationsSummary = async () => {
        const [ordersResult, materialsResult] = await Promise.allSettled([
            api.request("/api/v1/orders"),
            api.request("/api/v1/inventory/materials")
        ]);
        if (ordersResult.status === "fulfilled" && Array.isArray(ordersResult.value)) {
            const orders = ordersResult.value;
            const pendingOrders = orders.filter((order) =>
                ["PENDING", "FOR_CONFIRMATION"].includes(order.status)).length;
            const productionOrders = orders.filter((order) => order.status === "IN_PRODUCTION").length;
            const today = new Date();
            const salesCents = orders.reduce((total, order) => {
                if (order.paymentStatus !== "PAID" || !order.paidAt) return total;
                const paidAt = new Date(order.paidAt);
                if (Number.isNaN(paidAt.getTime())
                    || paidAt.getFullYear() !== today.getFullYear()
                    || paidAt.getMonth() !== today.getMonth()
                    || paidAt.getDate() !== today.getDate()) {
                    return total;
                }
                return total + Number(order.paidCents);
            }, 0);
            setMetric("pending-orders", String(pendingOrders), "Awaiting confirmation or processing");
            setMetric("in-production", String(productionOrders), "Orders currently in production");
            setMetric("today-sales", new Intl.NumberFormat("en-PH", {
                style: "currency",
                currency: "PHP"
            }).format(salesCents / 100), "Paid POS orders recorded today");
        } else {
            const message = ordersResult.status === "rejected"
                ? errorMessage(ordersResult.reason)
                : "The orders API returned an invalid response.";
            setMetric("pending-orders", "—", `Unable to load orders: ${message}`);
            setMetric("in-production", "—", `Unable to load orders: ${message}`);
            setMetric("today-sales", "—", `Unable to load sales: ${message}`);
        }

        if (materialsResult.status === "fulfilled" && Array.isArray(materialsResult.value)) {
            renderInventory(materialsResult.value);
        } else {
            const message = materialsResult.status === "rejected"
                ? errorMessage(materialsResult.reason)
                : "The inventory API returned an invalid response.";
            inventoryBody.replaceChildren();
            inventoryEmpty.hidden = false;
            inventoryEmpty.textContent = `Inventory could not be loaded: ${message}`;
            setMetric("stock-alerts", "—", `Unable to load inventory: ${message}`);
        }
    };

    const render = () => {
        const filtered = filteredRequests();
        const pageCount = Math.ceil(filtered.length / pageSize);
        page = Math.max(1, Math.min(page, pageCount || 1));
        const start = (page - 1) * pageSize;
        const pageItems = filtered.slice(start, start + pageSize);
        tableBody.replaceChildren();
        pageItems.forEach((request) => {
            const row = document.createElement("tr");
            const checkCell = document.createElement("td");
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.disabled = true;
            checkbox.setAttribute("aria-label", `${request.requestId} is read-only in this list`);
            checkCell.append(checkbox);
            row.append(checkCell);
            [
                request.requestId,
                request.customerName || "—",
                request.referenceImageName || "Custom mirror request",
                titleCase(request.status),
                new Date(request.submittedAt).toLocaleDateString()
            ].forEach((value, index) => {
                const cell = document.createElement("td");
                cell.textContent = value;
                if (index === 0) cell.className = "all-orders-id";
                if (index === 3) cell.className = "all-orders-badge";
                row.append(cell);
            });
            const actionCell = document.createElement("td");
            actionCell.append(reviewLink(request));
            row.append(actionCell);
            tableBody.append(row);
        });

        emptyState.hidden = filtered.length > 0;
        countLabel.textContent = filtered.length === 0
            ? "Showing 0 of 0 requests"
            : `Showing ${start + 1}–${Math.min(start + pageSize, filtered.length)} of ${filtered.length} requests`;
        pageButtons.replaceChildren();
        for (let number = 1; number <= pageCount; number += 1) {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = String(number);
            button.setAttribute("aria-label", `Page ${number}`);
            button.setAttribute("aria-current", number === page ? "page" : "false");
            button.classList.toggle("is-active", number === page);
            button.addEventListener("click", () => {
                page = number;
                render();
            });
            pageButtons.append(button);
        }
        document.getElementById("orders-previous-page").disabled = page <= 1;
        document.getElementById("orders-next-page").disabled = page >= pageCount;
    };

    const loadRequests = async () => {
        countLabel.textContent = "Loading requests…";
        try {
            requests = await api.request("/api/v1/admin/custom-orders");
            if (!Array.isArray(requests)) {
                throw new Error("The custom requests API returned an invalid response.");
            }
            requestsLoadError = "";
            renderDashboard();
            render();
        } catch (error) {
            requests = [];
            requestsLoadError = error.message || "Unknown error.";
            renderDashboard();
            render();
            countLabel.textContent = `Requests could not be loaded: ${error.message}`;
        }
    };

    document.getElementById("view-all-orders").addEventListener("click", async () => {
        page = 1;
        dialog.showModal();
        await loadRequests();
    });
    dashboardFilters.forEach((button) => {
        button.addEventListener("click", () => {
            activeDashboardStatus = button.dataset.orderStatus;
            dashboardFilters.forEach((candidate) => {
                const active = candidate === button;
                candidate.classList.toggle("is-active", active);
                candidate.setAttribute("aria-pressed", String(active));
            });
            renderDashboard();
        });
    });
    dashboardSearch.addEventListener("input", renderDashboard);
    document.querySelector('[data-action="export"]').addEventListener("click", () => {
        const rows = [
            ["Request ID", "Customer", "Reference", "Status", "Submitted"],
            ...filteredDashboardRequests().map((request) => [
                request.requestId,
                request.customerName || "",
                request.referenceImageName || "",
                titleCase(request.status),
                new Date(request.submittedAt).toLocaleDateString()
            ])
        ];
        const csvCell = (value) => `"${String(value).replaceAll('"', '""')}"`;
        const csv = rows.map((row) => row.map(csvCell).join(",")).join("\r\n");
        const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
        const link = document.createElement("a");
        link.href = url;
        link.download = "style-maria-custom-requests.csv";
        link.click();
        URL.revokeObjectURL(url);
    });
    document.getElementById("close-all-orders").addEventListener("click", () => dialog.close());
    dialog.addEventListener("click", (event) => {
        if (event.target === dialog) dialog.close();
    });
    document.getElementById("orders-previous-page").addEventListener("click", () => {
        page -= 1;
        render();
    });
    document.getElementById("orders-next-page").addEventListener("click", () => {
        page += 1;
        render();
    });
    [search, statusFilter, dateFilter].forEach((control) => {
        control.addEventListener(control === search ? "input" : "change", () => {
            page = 1;
            render();
        });
    });
    document.getElementById("select-all-orders").disabled = true;
    document.getElementById("all-orders-bulk-actions").hidden = true;
    document.querySelector('[data-action="export-all-orders"]').addEventListener("click", () => {
        const filtered = filteredRequests();
        const rows = [
            ["Request ID", "Customer", "Reference image", "Status", "Submitted"],
            ...filtered.map((request) => [
                request.requestId,
                request.customerName || "",
                request.referenceImageName || "",
                titleCase(request.status),
                new Date(request.submittedAt).toLocaleDateString()
            ])
        ];
        const csvCell = (value) => `"${String(value).replaceAll('"', '""')}"`;
        const csv = rows.map((row) => row.map(csvCell).join(",")).join("\r\n");
        const downloadUrl = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
        const link = document.createElement("a");
        link.href = downloadUrl;
        link.download = "style-maria-custom-requests.csv";
        link.click();
        URL.revokeObjectURL(downloadUrl);
    });
    document.getElementById("today-date").textContent = new Intl.DateTimeFormat(undefined, {
        day: "numeric",
        month: "short",
        year: "numeric"
    }).format(new Date());
    loadRequests();
    loadOperationsSummary();
})();
