(() => {
    const section = document.getElementById("custom-request-details");
    if (!section) return;

    const loading = document.getElementById("custom-request-loading");
    const errorMessage = document.getElementById("custom-request-error");
    const form = document.getElementById("custom-request-admin-form");
    const feedback = document.getElementById("custom-request-save-feedback");
    const statusPill = document.getElementById("request-status-pill");
    const sendButton = document.getElementById("send-official-quote");
    const requestId = new URLSearchParams(window.location.search).get("request");
    const amountInputs = [
        document.getElementById("quote-base-amount"),
        document.getElementById("quote-frame-amount"),
        document.getElementById("quote-labor-amount"),
        document.getElementById("quote-delivery-amount")
    ];
    let saving = false;

    const showError = (message) => {
        loading.hidden = true;
        section.hidden = true;
        errorMessage.textContent = message;
        errorMessage.hidden = false;
    };

    const displayValue = (value) => value && String(value).trim() ? value : "—";
    const statusLabel = (status) => status.replaceAll("_", " ")
        .toLocaleLowerCase()
        .replace(/\b\w/g, (letter) => letter.toUpperCase());
    const currency = new Intl.NumberFormat("en-PH", {
        style: "currency",
        currency: "PHP",
        minimumFractionDigits: 2
    });

    const setStatusPill = (status) => {
        statusPill.textContent = statusLabel(status);
        statusPill.className = "custom-request-id-badge custom-request-status-pill";
        if (status === "NEW_REQUEST") statusPill.classList.add("is-new");
        if (status === "FOR_QUOTATION") statusPill.classList.add("is-quote");
        if (status === "NEEDS_INFO") statusPill.classList.add("is-needs-info");
    };

    const quotePayload = () => ({
        baseMirrorAmount: amountInputs[0].value,
        frameAmount: amountInputs[1].value,
        laborAmount: amountInputs[2].value,
        deliveryAmount: amountInputs[3].value,
        notes: document.getElementById("request-admin-notes").value
    });

    const updateEstimatedTotal = () => {
        const total = amountInputs.reduce((sum, input) => {
            const value = Number(input.value);
            return sum + (Number.isFinite(value) && value >= 0 ? value : 0);
        }, 0);
        document.getElementById("quote-estimated-total").textContent = currency.format(total);
    };

    const loadRequest = async () => {
        if (!requestId) {
            showError("No custom request was selected. Return to the Custom Requests list.");
            return;
        }

        try {
            const response = await fetch(`/api/v1/admin/custom-orders/${encodeURIComponent(requestId)}`);
            if (response.status === 404) {
                showError("This custom request could not be found.");
                return;
            }
            if (!response.ok) throw new Error(`Request details returned ${response.status}.`);
            const request = await response.json();

            document.getElementById("request-id").textContent = request.requestId;
            const submitted = new Date(request.submittedAt);
            const submittedElement = document.getElementById("request-submitted");
            submittedElement.dateTime = submitted.toISOString();
            submittedElement.textContent = submitted.toLocaleString();
            document.getElementById("customer-name").textContent = displayValue(request.customerName);
            document.getElementById("customer-email").textContent = displayValue(request.email);
            document.getElementById("customer-contact").textContent = displayValue(request.contactNumber);
            document.getElementById("request-dimensions").textContent =
                `${request.length} × ${request.width}`;
            document.getElementById("request-unit").textContent =
                request.unit === "in" ? "Inches (in)" : "Centimeters (cm)";
            document.getElementById("request-frame").textContent =
                request.frame === "Custom (Request)" ? "Custom Request" : request.frame;
            document.getElementById("request-custom-frame").textContent =
                displayValue(request.customFrameRequest);
            document.getElementById("custom-frame-row").hidden =
                request.frame !== "Custom (Request)";
            document.getElementById("request-message").textContent = displayValue(request.message);
            setStatusPill(request.status);
            document.getElementById("quote-base-description").textContent =
                `Base Mirror (${request.length} × ${request.width} ${request.unit})`;
            document.getElementById("quote-frame-description").textContent =
                `Frame (${request.frame === "Custom (Request)" ? "Custom Request" : request.frame})`;
            amountInputs[0].value = request.quoteBaseMirrorAmount ?? "0.00";
            amountInputs[1].value = request.quoteFrameAmount ?? "0.00";
            amountInputs[2].value = request.quoteLaborAmount ?? "0.00";
            amountInputs[3].value = request.quoteDeliveryAmount ?? "0.00";
            document.getElementById("request-admin-notes").value = request.notes || "";
            updateEstimatedTotal();

            if (request.paymentSubmittedAt) {
                document.getElementById("admin-payment-submitted").textContent =
                    new Date(request.paymentSubmittedAt).toLocaleString();
                document.getElementById("admin-payment-status").textContent =
                    statusLabel(request.paymentStatus || "SUBMITTED");
                document.getElementById("admin-payment-amount").textContent =
                    currency.format(Number(request.paymentAmount || 0));
                document.getElementById("admin-payment-method").textContent =
                    displayValue(request.paymentMethod);
                document.getElementById("admin-payment-reference").textContent =
                    displayValue(request.paymentReference);
                const proofLink = document.getElementById("admin-payment-proof");
                proofLink.href = `/api/v1/admin/custom-orders/${encodeURIComponent(request.requestId)}/payment-proof`;
                proofLink.textContent = request.receiptName
                    ? `View uploaded proof (${request.receiptName})`
                    : "View uploaded proof";
                document.getElementById("admin-payment-review").hidden = false;
            }

            const deliveryMessage = document.getElementById("quote-delivery-note");
            deliveryMessage.textContent = "Publishing makes the quote available on the customer’s Track Order page.";
            if (request.quoteSentAt) {
                const sentDate = new Date(request.quoteSentAt).toLocaleString();
                deliveryMessage.textContent = `Quote published to the customer’s Track Order page on ${sentDate}.`;
                sendButton.textContent = "➤ Publish Updated Quote";
            }

            if (request.referenceImageName) {
                const image = document.getElementById("request-reference-image");
                image.src = `/api/v1/admin/custom-orders/${encodeURIComponent(request.requestId)}/reference-image`;
                image.hidden = false;
                document.getElementById("request-image-empty").hidden = true;
                image.addEventListener("error", () => {
                    image.hidden = true;
                    document.getElementById("request-image-empty").hidden = false;
                }, { once: true });
            }

            loading.hidden = true;
            section.hidden = false;
        } catch (error) {
            console.error("Unable to load custom request details.", error);
            showError("Unable to load this custom request. Refresh the page to try again.");
        }
    };

    amountInputs.forEach((input) => input.addEventListener("input", updateEstimatedTotal));

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (saving || !form.reportValidity()) return;

        const publishQuote = event.submitter?.id === "send-official-quote";
        const endpoint = publishQuote
            ? `/api/v1/admin/custom-orders/${encodeURIComponent(requestId)}/publish-quote`
            : `/api/v1/admin/custom-orders/${encodeURIComponent(requestId)}/quote-draft`;
        feedback.hidden = true;
        saving = true;
        form.querySelectorAll("button").forEach((button) => {
            button.disabled = true;
        });
        feedback.textContent = publishQuote ? "Publishing the official quote..." : "Saving quote draft...";
        feedback.className = "";
        feedback.hidden = false;

        try {
            const response = await fetch(endpoint, {
                method: publishQuote ? "POST" : "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(quotePayload())
            });
            const result = await response.json();
            if (!response.ok) {
                throw new Error(result.message || "The quote could not be saved.");
            }
            document.getElementById("quote-estimated-total").textContent = currency.format(Number(result.total));
            if (publishQuote) {
                setStatusPill(result.status);
                document.getElementById("quote-delivery-note").textContent =
                    "The official quote is now available on the customer’s Track Order page.";
                feedback.textContent = "Official quote published successfully.";
                sendButton.textContent = "➤ Publish Updated Quote";
            } else {
                feedback.textContent = "Quote draft saved.";
            }
            feedback.className = "custom-request-save-success";
            feedback.hidden = false;
        } catch (error) {
            console.error(publishQuote
                ? "Unable to publish the official custom-order quote."
                : "Unable to save the custom-order quote draft.", error);
            feedback.textContent = error.message || "Unable to save this quote. Please try again.";
            feedback.className = "custom-request-save-error";
            feedback.hidden = false;
        } finally {
            saving = false;
            form.querySelectorAll("button").forEach((button) => {
                button.disabled = false;
            });
        }
    });

    loadRequest();
})();
