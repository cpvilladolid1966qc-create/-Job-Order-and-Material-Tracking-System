(() => {
    const params = new URLSearchParams(window.location.search);
    const orderId = params.get("orderId");
    const loading = document.getElementById("payment-loading");
    const error = document.getElementById("payment-error");
    const content = document.getElementById("payment-content");
    const form = document.getElementById("payment-form");
    const submitButton = document.getElementById("submit-payment");
    const feedback = document.getElementById("payment-feedback");
    const receiptInput = document.getElementById("receipt");
    const receiptName = document.getElementById("receipt-name");
    const removeReceiptButton = document.getElementById("remove-receipt");
    const receiptUploadLabel = document.getElementById("receipt-upload-label");
    const paymentMethodCards = [...document.querySelectorAll(".payment-method-card")];

    const setOpenPaymentMethod = (selectedCard) => {
        paymentMethodCards.forEach((card) => {
            const radio = card.querySelector('input[name="paymentMethod"]');
            const toggle = card.querySelector(".payment-method-toggle");
            const panel = document.getElementById(toggle.getAttribute("aria-controls"));
            const isOpen = card === selectedCard;
            panel.hidden = !isOpen;
            toggle.setAttribute("aria-expanded", String(isOpen));
            toggle.setAttribute(
                "aria-label",
                `${isOpen ? "Hide" : "Show"} ${radio.value} QR code`);
            card.classList.toggle("is-selected", radio.checked);
            card.classList.toggle("is-expanded", isOpen);
        });
    };

    paymentMethodCards.forEach((card) => {
        const radio = card.querySelector('input[name="paymentMethod"]');
        const toggle = card.querySelector(".payment-method-toggle");
        radio.addEventListener("change", () => setOpenPaymentMethod(card));
        toggle.addEventListener("click", () => {
            const panel = document.getElementById(toggle.getAttribute("aria-controls"));
            if (!panel.hidden) {
                setOpenPaymentMethod(null);
                return;
            }
            radio.checked = true;
            setOpenPaymentMethod(card);
        });
    });

    const updateReceiptSelection = () => {
        const file = receiptInput.files[0];
        receiptName.textContent = file
            ? `${file.name} (${(file.size / 1024 / 1024).toFixed(2)} MB)`
            : "No file selected";
        removeReceiptButton.hidden = !file;
    };

    const money = (value) => new Intl.NumberFormat("en-PH", {
        style: "currency",
        currency: "PHP"
    }).format(Number(value));

    const showError = (message) => {
        loading.hidden = true;
        error.textContent = message;
        error.hidden = false;
    };

    const loadOrder = async () => {
        if (!orderId) {
            showError("Open payment from the quotation in My Orders so we can find the correct request.");
            return;
        }
        try {
            const response = await fetch(`/api/v1/customer/custom-orders/${encodeURIComponent(orderId)}`);
            const result = await response.json();
            if (response.status === 401) {
                window.location.href = "/?error=unauthorized";
                return;
            }
            if (!response.ok) throw new Error(result.message || "Unable to load this quotation.");
            if (!result.quoteSentAt || result.quoteTotal == null) {
                throw new Error("This request does not have a published quotation yet.");
            }

            document.getElementById("payment-order-id").textContent = result.requestId;
            const submittedAt = result.submittedAt ? new Date(result.submittedAt) : null;
            document.getElementById("payment-submitted").textContent =
                submittedAt && !Number.isNaN(submittedAt.getTime())
                    ? submittedAt.toLocaleString()
                    : "—";
            document.getElementById("payment-dimensions").textContent =
                `${result.length} × ${result.width} ${result.unit}`;
            document.getElementById("payment-frame").textContent =
                result.frame === "Custom (Request)" ? "Custom Request" : result.frame;
            document.getElementById("payment-quote-total").textContent = money(result.quoteTotal);
            document.getElementById("payment-downpayment").textContent =
                money(result.quoteDownpayment);
            if (result.referenceImageName) {
                document.getElementById("payment-reference-image").src =
                    `/api/v1/customer/custom-orders/${encodeURIComponent(result.requestId)}/reference-image`;
                document.getElementById("payment-reference-panel").hidden = false;
            }
            loading.hidden = true;
            content.hidden = false;

            if (result.paymentSubmittedAt) {
                document.getElementById("payment-already-submitted").hidden = false;
                form.hidden = true;
                return;
            }
            submitButton.disabled = false;
        } catch (loadError) {
            console.error("Unable to load custom order quotation.", loadError);
            showError(loadError.message || "Unable to load this quotation. Please try again.");
        }
    };

    receiptInput.addEventListener("change", updateReceiptSelection);
    removeReceiptButton.addEventListener("click", () => {
        receiptInput.value = "";
        updateReceiptSelection();
        receiptUploadLabel.focus();
    });

    const uploadBox = document.querySelector(".payment-upload-box");
    receiptUploadLabel.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            receiptInput.click();
        }
    });
    uploadBox.addEventListener("dragover", (event) => {
        event.preventDefault();
        uploadBox.classList.add("is-dragging");
    });
    uploadBox.addEventListener("dragleave", () => {
        uploadBox.classList.remove("is-dragging");
    });
    uploadBox.addEventListener("drop", (event) => {
        event.preventDefault();
        uploadBox.classList.remove("is-dragging");
        if (!event.dataTransfer.files.length) return;
        receiptInput.files = event.dataTransfer.files;
        updateReceiptSelection();
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        submitButton.disabled = true;
        feedback.hidden = true;
        const formData = new FormData(form);
        try {
            const response = await fetch(
                `/api/v1/custom-orders/${encodeURIComponent(orderId)}/checkout`,
                { method: "POST", body: formData });
            const result = await response.json();
            if (!response.ok) throw new Error(result.message || "Unable to submit payment proof.");
            form.hidden = true;
            const notice = document.getElementById("payment-already-submitted");
            notice.hidden = false;
            notice.replaceChildren(
                document.createTextNode(
                    "Your payment proof has been submitted and is awaiting shop verification. "),
                Object.assign(document.createElement("a"), {
                    href: "/track-order.html",
                    textContent: "Return to My Orders"
                }));
        } catch (submitError) {
            console.error("Unable to submit payment proof.", submitError);
            feedback.textContent = submitError.message || "Unable to submit payment proof. Please try again.";
            feedback.hidden = false;
            submitButton.disabled = false;
        }
    });

    loadOrder();
})();
