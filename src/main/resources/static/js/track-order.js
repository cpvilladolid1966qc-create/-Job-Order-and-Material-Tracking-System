(() => {
    const ordersBody = document.getElementById("track-orders-body");
    if (!ordersBody) return;

    const search = document.getElementById("track-order-search");
    const emptyState = document.getElementById("track-orders-empty");
    const detailDialog = document.getElementById("track-order-detail-dialog");
    const quoteDialog = document.getElementById("customer-quote-dialog");
    const detailError = document.getElementById("track-order-detail-error");
    let orders = [];

    const formatDate = (value) => value
        ? new Intl.DateTimeFormat(undefined, {
            dateStyle: "medium",
            timeStyle: "short"
        }).format(new Date(value))
        : "—";

    const statusLabel = (status) => ({
        NEW_REQUEST: "New Request",
        FOR_QUOTATION: "For Quotation",
        QUOTATION_SENT: "Quotation Sent",
        PAYMENT_SUBMITTED: "Payment Submitted",
        NEEDS_INFO: "Needs Info",
        IN_PRODUCTION: "In Production",
        COMPLETED: "Completed",
        CANCELLED: "Cancelled"
    })[status] || status.replaceAll("_", " ");

    const progressSteps = [
        { title: "Request Submitted", description: "Your request was received." },
        { title: "Admin Review", description: "Our team is reviewing your request." },
        { title: "Quotation", description: "Your official quote will appear here when sent." },
        { title: "Production", description: "Production begins after the quote is approved." },
        { title: "Completed", description: "Your custom order is complete." }
    ];

    const progressStepForStatus = (status) => ({
        NEW_REQUEST: 2,
        FOR_QUOTATION: 2,
        NEEDS_INFO: 2,
        QUOTATION_SENT: 3,
        PAYMENT_SUBMITTED: 4,
        IN_PRODUCTION: 4,
        COMPLETED: 5,
        CANCELLED: 0
    })[status] ?? 1;

    const renderProgress = (order) => {
        const status = order.status || "NEW_REQUEST";
        const currentStep = progressStepForStatus(status);
        const progress = document.getElementById("track-order-progress");
        const isCancelled = status === "CANCELLED";

        progress.replaceChildren(...progressSteps.map((step, index) => {
            const stepNumber = index + 1;
            const item = document.createElement("li");
            item.className = "track-order-progress-step";
            if (!isCancelled && stepNumber < currentStep) item.classList.add("is-complete");
            if (!isCancelled && stepNumber === currentStep) {
                item.classList.add("is-current");
                item.setAttribute("aria-current", "step");
            }

            const marker = document.createElement("span");
            marker.className = "track-order-progress-marker";
            marker.textContent = String(stepNumber);
            const title = document.createElement("strong");
            title.textContent = step.title;
            const description = document.createElement("span");
            description.className = "track-order-progress-description";
            description.textContent = stepNumber === 1
                ? formatDate(order.submittedAt)
                : stepNumber === 3 && order.quoteSentAt
                    ? formatDate(order.quoteSentAt)
                    : stepNumber === currentStep
                        ? statusLabel(status)
                        : step.description;
            item.append(marker, title, description);
            return item;
        }));

        const updatedAt = status === "QUOTATION_SENT"
            ? order.quoteSentAt || order.statusUpdatedAt || order.submittedAt
            : order.statusUpdatedAt || order.quoteSentAt || order.submittedAt;
        const updated = document.getElementById("track-order-progress-updated");
        updated.textContent = isCancelled
            ? `This request was cancelled${updatedAt ? ` on ${formatDate(updatedAt)}` : ""}.`
            : `Last updated: ${formatDate(updatedAt)}`;
    };

    const createOrderRow = (order) => {
        const row = document.createElement("tr");
        const idCell = document.createElement("td");
        idCell.textContent = order.requestId;
        row.append(idCell);

        const previewCell = document.createElement("td");
        if (order.referenceImageName) {
            const image = document.createElement("img");
            image.className = "track-order-preview";
            image.src = `/api/v1/customer/custom-orders/${encodeURIComponent(order.requestId)}/reference-image`;
            image.alt = `Reference design for ${order.requestId}`;
            previewCell.append(image);
        } else {
            const placeholder = document.createElement("span");
            placeholder.className = "track-order-no-preview";
            placeholder.textContent = "No image";
            previewCell.append(placeholder);
        }
        row.append(previewCell);

        const submittedCell = document.createElement("td");
        submittedCell.textContent = formatDate(order.submittedAt);
        row.append(submittedCell);

        const statusCell = document.createElement("td");
        const status = document.createElement("span");
        status.className = `track-order-status status-${order.status.toLowerCase().replaceAll("_", "-")}`;
        status.textContent = statusLabel(order.status);
        statusCell.append(status);
        row.append(statusCell);

        const updatedCell = document.createElement("td");
        updatedCell.textContent = formatDate(order.quoteSentAt || order.statusUpdatedAt || order.submittedAt);
        row.append(updatedCell);

        const actionCell = document.createElement("td");
        const detailButton = document.createElement("button");
        detailButton.className = "track-order-view-button";
        detailButton.type = "button";
        detailButton.textContent = "View Details";
        detailButton.addEventListener("click", () => loadDetails(order.requestId));
        actionCell.append(detailButton);
        row.append(actionCell);
        return row;
    };

    const renderOrders = () => {
        const query = search.value.trim().toLocaleLowerCase();
        const visible = orders.filter((order) => order.requestId.toLocaleLowerCase().includes(query));
        ordersBody.replaceChildren(...visible.map(createOrderRow));
        emptyState.hidden = visible.length > 0;
        emptyState.textContent = orders.length === 0
            ? "You haven't submitted any custom order requests yet."
            : "No requests match that request ID.";
    };

    const setDetailText = (id, value) => {
        document.getElementById(id).textContent = value || "—";
    };

    const loadDetails = async (requestId) => {
        detailError.hidden = true;
        document.getElementById("detail-quote-notice").hidden = true;
        document.getElementById("detail-quote-notes-row").hidden = true;
        document.getElementById("detail-image-row").hidden = true;
        document.getElementById("proceed-to-payment").hidden = true;
        try {
            const response = await fetch(
                `/api/v1/customer/custom-orders/${encodeURIComponent(requestId)}`);
            const result = await response.json();
            if (!response.ok) {
                throw new Error(result.message || "Unable to load this request.");
            }
            setDetailText("detail-request-id", result.requestId);
            setDetailText("detail-submitted", formatDate(result.submittedAt));
            setDetailText("detail-dimensions", `${result.length} × ${result.width}`);
            setDetailText("detail-unit", result.unit);
            setDetailText("detail-frame", result.frame === "Custom (Request)" ? "Custom Request" : result.frame);
            setDetailText("detail-custom-frame", result.customFrameRequest);
            document.getElementById("detail-custom-frame-row").hidden =
                result.frame !== "Custom (Request)";
            document.getElementById("detail-message-row").hidden = !result.message;
            setDetailText("detail-message", result.message);
            const statusBadge = document.getElementById("track-order-detail-status");
            statusBadge.textContent = statusLabel(result.status || "NEW_REQUEST");
            statusBadge.className =
                `track-order-status status-${(result.status || "new_request").toLowerCase().replaceAll("_", "-")}`;
            renderProgress(result);

            if (result.referenceImageName) {
                const image = document.getElementById("detail-reference-image");
                image.onerror = () => {
                    document.getElementById("detail-image-row").hidden = true;
                };
                image.src = `/api/v1/customer/custom-orders/${encodeURIComponent(result.requestId)}/reference-image`;
                document.getElementById("detail-image-row").hidden = false;
            } else {
                document.getElementById("detail-reference-image").removeAttribute("src");
            }

            if (result.quoteSentAt && result.quoteTotal != null) {
                const money = (amount) => new Intl.NumberFormat("en-PH", {
                    style: "currency",
                    currency: "PHP"
                }).format(Number(amount));
                setDetailText("detail-quote-base", money(result.quoteBaseMirrorAmount));
                setDetailText("detail-quote-frame", money(result.quoteFrameAmount));
                setDetailText("detail-quote-labor", money(result.quoteLaborAmount));
                setDetailText("detail-quote-delivery", money(result.quoteDeliveryAmount));
                setDetailText("detail-quote-total", money(result.quoteTotal));
                if (result.quoteNotes) {
                    setDetailText("detail-quote-notes", result.quoteNotes);
                    document.getElementById("detail-quote-notes-row").hidden = false;
                }
                document.getElementById("detail-quote-notice").hidden = false;
                const paymentSubmitted = Boolean(result.paymentSubmittedAt);
                document.getElementById("detail-quote-notice-title").textContent = paymentSubmitted
                    ? "Payment proof submitted."
                    : "Your quotation is ready.";
                document.getElementById("detail-quote-notice-description").textContent = paymentSubmitted
                    ? "The shop will verify your payment proof and contact you about the remaining balance."
                    : "Review the quotation and continue to payment when ready.";
                document.getElementById("proceed-to-payment").hidden = paymentSubmitted;
            }
            detailDialog.showModal();
        } catch (error) {
            console.error("Unable to load customer custom order details.", error);
            detailError.textContent = error.message || "Unable to load this request. Please try again.";
            detailError.hidden = false;
            detailDialog.showModal();
        }
    };

    document.getElementById("close-track-order-details").addEventListener("click", () => {
        detailDialog.close();
    });
    document.getElementById("back-to-my-orders").addEventListener("click", (event) => {
        event.preventDefault();
        detailDialog.close();
    });
    document.getElementById("view-customer-quote").addEventListener("click", () => {
        quoteDialog.showModal();
    });
    document.getElementById("proceed-to-payment").addEventListener("click", () => {
        const requestId = document.getElementById("detail-request-id").textContent;
        if (requestId) {
            window.location.href = `/payment.html?orderId=${encodeURIComponent(requestId)}`;
        }
    });
    document.getElementById("close-customer-quote").addEventListener("click", () => {
        quoteDialog.close();
    });
    detailDialog.addEventListener("click", (event) => {
        if (event.target === detailDialog) detailDialog.close();
    });
    quoteDialog.addEventListener("click", (event) => {
        if (event.target === quoteDialog) quoteDialog.close();
    });
    search.addEventListener("input", renderOrders);

    const loadOrders = async () => {
        try {
            const response = await fetch("/api/v1/customer/custom-orders");
            if (response.status === 401) {
                window.location.href = "/?error=unauthorized";
                return;
            }
            if (!response.ok) throw new Error(`Order list returned ${response.status}.`);
            orders = await response.json();
            renderOrders();
        } catch (error) {
            console.error("Unable to load customer custom orders.", error);
            emptyState.textContent = "Unable to load your requests. Refresh the page to try again.";
            emptyState.hidden = false;
        }
    };

    loadOrders();
})();
