(() => {
    const form = document.getElementById("custom-order-form");
    const preview = document.getElementById("mirror-preview");
    const previewFrame = document.querySelector(".custom-order-preview");
    const previewPlaceholder = document.getElementById("preview-placeholder");
    const unitSelect = document.getElementById("dimension-unit");
    const lengthInput = document.getElementById("mirror-length");
    const widthInput = document.getElementById("mirror-width");
    const fileInput = document.getElementById("reference-image");
    const uploadBox = document.getElementById("reference-upload");
    const fileName = document.getElementById("upload-filename");
    const removeFileButton = document.getElementById("remove-reference-image");
    const dimensionError = document.getElementById("dimension-error");
    const uploadError = document.getElementById("upload-error");
    const feedback = document.getElementById("custom-order-feedback");
    const customFrameField = document.getElementById("custom-frame-request-field");
    const customFrameInput = document.getElementById("custom-frame-request");
    const customFrameError = document.getElementById("custom-frame-error");
    const messageInput = document.getElementById("custom-order-message");
    const messageCount = document.getElementById("message-character-count");
    const submitButton = form ? form.querySelector('button[type="submit"]') : null;
    const pageHeading = document.getElementById("custom-order-heading");
    const reviewScreen = document.getElementById("custom-order-review");
    const reviewTitle = document.getElementById("review-title");
    const reviewReferenceImage = document.getElementById("review-reference-image");
    const reviewPlaceholder = document.getElementById("review-preview-placeholder");
    const reviewFeedback = document.getElementById("review-feedback");
    const reviewSubmitButton = document.getElementById("submit-custom-order");
    const checkoutScreen = document.getElementById("checkout-screen");
    const checkoutTitle = document.getElementById("checkout-title");
    const confirmationScreen = document.getElementById("custom-order-confirmation");
    const confirmationTitle = document.getElementById("confirmation-title");
    const checkoutForm = document.getElementById("checkout-form");
    const checkoutSubmitButton = document.getElementById("place-demo-order");
    const checkoutFeedback = document.getElementById("checkout-feedback");
    const checkoutError = document.getElementById("checkout-error");
    const checkoutReceipt = document.getElementById("checkout-receipt");
    const checkoutReceiptName = document.getElementById("checkout-receipt-name");
    const checkoutPaymentQrTitle = document.getElementById("checkout-payment-qr-title");
    const checkoutPaymentQrImage = document.getElementById("checkout-payment-qr-image");
    const paymentMethodInputs = document.querySelectorAll('input[name="paymentMethod"]');
    const editButtons = [
        document.getElementById("edit-custom-order"),
        document.getElementById("back-to-customize")
    ];

    if (!form || !preview || !previewFrame || !previewPlaceholder || !unitSelect ||
        !lengthInput || !widthInput || !fileInput || !uploadBox || !fileName || !removeFileButton ||
        !dimensionError || !uploadError || !feedback || !customFrameField ||
        !customFrameInput || !customFrameError || !messageInput || !messageCount || !submitButton ||
        !pageHeading || !reviewScreen || !reviewTitle || !reviewReferenceImage || !reviewPlaceholder ||
        !reviewFeedback || !reviewSubmitButton || !checkoutScreen || !checkoutTitle ||
        !confirmationScreen || !confirmationTitle ||
        !checkoutForm || !checkoutSubmitButton || !checkoutFeedback || !checkoutError ||
        !checkoutReceipt || !checkoutReceiptName || !checkoutPaymentQrTitle || !checkoutPaymentQrImage ||
        paymentMethodInputs.length === 0 || editButtons.some((button) => !button)) {
        throw new Error("The custom order page is missing a required form element.");
    }

    const maxFileSize = 10 * 1024 * 1024;
    let selectedFile = null;
    let referenceImageUrl = null;

    const updatePaymentQr = () => {
        const selectedMethod = checkoutForm.querySelector('input[name="paymentMethod"]:checked');
        if (!selectedMethod) return;
        checkoutPaymentQrTitle.textContent = `${selectedMethod.value} QR code`;
        checkoutPaymentQrImage.alt = `QR code shown for ${selectedMethod.value}`;
    };

    paymentMethodInputs.forEach((input) => {
        input.addEventListener("change", updatePaymentQr);
    });
    updatePaymentQr();

    const isCustomFrameSelected = () =>
        form.querySelector('input[name="frame"]:checked')?.value === "Custom (Request)";

    const updateCustomFrameField = () => {
        const isCustom = isCustomFrameSelected();
        customFrameField.hidden = !isCustom;
        customFrameInput.required = isCustom;
        if (!isCustom) {
            customFrameInput.value = "";
            customFrameError.hidden = true;
            customFrameInput.removeAttribute("aria-invalid");
        }
    };

    const showReferenceImage = (file) => {
        if (referenceImageUrl) {
            URL.revokeObjectURL(referenceImageUrl);
            referenceImageUrl = null;
        }

        if (file) {
            referenceImageUrl = URL.createObjectURL(file);
            preview.src = referenceImageUrl;
            preview.alt = `Customer reference image: ${file.name}`;
            preview.hidden = false;
            previewPlaceholder.hidden = true;
            previewFrame.classList.add("has-reference");
            return;
        }

        preview.removeAttribute("src");
        preview.hidden = true;
        previewPlaceholder.hidden = false;
        previewFrame.classList.remove("has-reference");
    };

    const showFeedback = (message, isError = false) => {
        feedback.textContent = message;
        feedback.classList.toggle("is-error", isError);
        feedback.hidden = false;
    };

    unitSelect.addEventListener("change", () => {
        const oldUnit = unitSelect.dataset.currentUnit || "in";
        const newUnit = unitSelect.value;
        if (oldUnit === newUnit) return;

        const conversion = oldUnit === "in" ? 2.54 : 1 / 2.54;
        [lengthInput, widthInput].forEach((input) => {
            const value = Number(input.value);
            if (Number.isFinite(value) && value > 0) {
                input.value = String(Math.round(value * conversion * 10) / 10);
            }
        });

        const maxValue = newUnit === "in" ? "500" : "1270";
        lengthInput.max = maxValue;
        widthInput.max = maxValue;
        document.querySelectorAll(".dimension-unit").forEach((label) => {
            label.textContent = newUnit;
        });
        unitSelect.dataset.currentUnit = newUnit;
        dimensionError.hidden = true;
        lengthInput.removeAttribute("aria-invalid");
        widthInput.removeAttribute("aria-invalid");
    });

    const validateFile = (file) => {
        if (!file) return true;
        const isAllowedType = ["image/jpeg", "image/png"].includes(file.type) ||
            /\.(jpe?g|png)$/i.test(file.name);
        if (!isAllowedType) {
            uploadError.textContent = "Choose a JPG or PNG image.";
            uploadError.hidden = false;
            return false;
        }
        if (file.size > maxFileSize) {
            uploadError.textContent = "The image must be 10MB or smaller.";
            uploadError.hidden = false;
            return false;
        }
        uploadError.hidden = true;
        return true;
    };

    const setSelectedFile = (file) => {
        if (!validateFile(file)) {
            selectedFile = null;
            fileInput.value = "";
            fileName.textContent = "";
            removeFileButton.hidden = true;
            showReferenceImage(null);
            return;
        }
        selectedFile = file;
        fileName.textContent = file ? file.name : "";
        removeFileButton.hidden = !file;
        showReferenceImage(file);
    };

    fileInput.addEventListener("change", () => {
        setSelectedFile(fileInput.files[0] || null);
    });

    removeFileButton.addEventListener("click", () => {
        fileInput.value = "";
        fileName.textContent = "";
        selectedFile = null;
        removeFileButton.hidden = true;
        uploadError.hidden = true;
        showReferenceImage(null);
    });

    form.querySelectorAll('input[name="frame"]').forEach((input) => {
        input.addEventListener("change", updateCustomFrameField);
    });
    updateCustomFrameField();

    messageInput.addEventListener("input", () => {
        messageCount.textContent = `${messageInput.value.length}/500`;
    });

    ["dragenter", "dragover"].forEach((eventName) => {
        uploadBox.addEventListener(eventName, (event) => {
            event.preventDefault();
            uploadBox.classList.add("is-dragging");
        });
    });

    ["dragleave", "drop"].forEach((eventName) => {
        uploadBox.addEventListener(eventName, (event) => {
            event.preventDefault();
            uploadBox.classList.remove("is-dragging");
        });
    });

    uploadBox.addEventListener("drop", (event) => {
        const file = event.dataTransfer.files[0];
        setSelectedFile(file || null);
    });

    window.addEventListener("pagehide", () => {
        if (referenceImageUrl) URL.revokeObjectURL(referenceImageUrl);
    }, { once: true });

    let checkoutCompleted = false;
    let requestSubmitted = false;
    const setPageStep = (step) => {
        const isCustomize = step === "customize";
        const isReview = step === "review";
        const isCheckout = step === "checkout";
        const isSubmitted = step === "submitted";
        form.hidden = !isCustomize;
        pageHeading.hidden = !isCustomize;
        reviewScreen.hidden = !isReview;
        checkoutScreen.hidden = !isCheckout;
        confirmationScreen.hidden = !isSubmitted;
        document.getElementById("review-breadcrumb-separator").hidden = !isReview;
        document.getElementById("review-breadcrumb-current").hidden = !isReview;
        document.getElementById("checkout-breadcrumb-separator").hidden = !isCheckout;
        document.getElementById("checkout-breadcrumb-current").hidden = !isCheckout;
        document.getElementById("submitted-breadcrumb-separator").hidden = !isSubmitted;
        document.getElementById("submitted-breadcrumb-current").hidden = !isSubmitted;
        document.title = isCheckout
            ? "Checkout & Payment | Style Maria"
            : isSubmitted ? "Custom Request Submitted | Style Maria"
            : isReview ? "Review Your Custom Order | Style Maria" : "Customize Your Mirror | Style Maria";
        if (isReview) reviewTitle.focus();
        if (isCheckout) checkoutTitle.focus();
        if (isSubmitted) confirmationTitle.focus();
        if (isCustomize) lengthInput.focus();
    };

    const updateReview = (length, width, frame) => {
        const unitLabel = unitSelect.value === "in" ? "inches (in)" : "centimeters (cm)";
        document.getElementById("review-dimensions").textContent =
            `${length} (H) × ${width} (W) ${unitSelect.value}`;
        document.getElementById("review-unit").textContent = unitLabel;
        document.getElementById("review-frame").textContent = frame.value;

        const customFrameRow = document.getElementById("review-custom-frame-row");
        customFrameRow.hidden = frame.value !== "Custom (Request)";
        document.getElementById("review-custom-frame").textContent = customFrameInput.value.trim();

        const referenceRow = document.getElementById("review-reference-row");
        referenceRow.hidden = !selectedFile;
        document.getElementById("review-reference-name").textContent =
            selectedFile ? selectedFile.name : "";
        reviewReferenceImage.hidden = !selectedFile;
        reviewPlaceholder.hidden = Boolean(selectedFile);
        if (selectedFile && referenceImageUrl) {
            reviewReferenceImage.src = referenceImageUrl;
        } else {
            reviewReferenceImage.removeAttribute("src");
        }

        const message = messageInput.value.trim();
        document.getElementById("review-message-row").hidden = !message;
        document.getElementById("review-message").textContent = message;

        document.getElementById("checkout-summary-dimensions").textContent =
            `${length} ${unitSelect.value} (H) × ${width} ${unitSelect.value} (W)`;
        document.getElementById("checkout-summary-frame").textContent = frame.value;
        const checkoutCustomFrame = document.getElementById("checkout-summary-custom-frame");
        checkoutCustomFrame.hidden = frame.value !== "Custom (Request)";
        checkoutCustomFrame.textContent = customFrameInput.value.trim();
        const checkoutMessage = document.getElementById("checkout-summary-message");
        checkoutMessage.hidden = !message;
        checkoutMessage.textContent = message;

        const checkoutImage = document.getElementById("checkout-reference-image");
        const checkoutImagePlaceholder = document.getElementById("checkout-image-placeholder");
        checkoutImage.hidden = !selectedFile;
        checkoutImagePlaceholder.hidden = Boolean(selectedFile);
        if (selectedFile && referenceImageUrl) {
            checkoutImage.src = referenceImageUrl;
        } else {
            checkoutImage.removeAttribute("src");
        }
    };

    editButtons.forEach((button) => {
        button.addEventListener("click", () => {
            reviewFeedback.hidden = true;
            setPageStep("customize");
        });
    });

    document.getElementById("back-to-review").addEventListener("click", () => {
        checkoutFeedback.hidden = true;
        setPageStep("review");
    });

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        feedback.hidden = true;
        dimensionError.hidden = true;
        uploadError.hidden = true;
        customFrameError.hidden = true;
        lengthInput.removeAttribute("aria-invalid");
        widthInput.removeAttribute("aria-invalid");
        customFrameInput.removeAttribute("aria-invalid");

        const length = Number(lengthInput.value);
        const width = Number(widthInput.value);
        const maxValue = unitSelect.value === "in" ? 500 : 1270;
        if (!Number.isFinite(length) || !Number.isFinite(width) ||
            length < 1 || width < 1 || length > maxValue || width > maxValue) {
            dimensionError.textContent = `Enter a length and width from 1 to ${maxValue} ${unitSelect.value}.`;
            dimensionError.hidden = false;
            lengthInput.setAttribute("aria-invalid", "true");
            widthInput.setAttribute("aria-invalid", "true");
            (length < 1 || length > maxValue || !Number.isFinite(length) ? lengthInput : widthInput).focus();
            return;
        }

        if (!validateFile(selectedFile)) {
            fileInput.focus();
            return;
        }

        const selectedFrame = form.querySelector('input[name="frame"]:checked');
        if (!selectedFrame) {
            showFeedback("Select a frame material to continue.", true);
            return;
        }

        const customFrameRequest = customFrameInput.value.trim();
        if (selectedFrame.value === "Custom (Request)" && !customFrameRequest) {
            customFrameError.textContent = "Describe the custom frame material, color, or finish you want.";
            customFrameError.hidden = false;
            customFrameInput.setAttribute("aria-invalid", "true");
            customFrameInput.focus();
            return;
        }

        updateReview(length, width, selectedFrame);
        reviewFeedback.hidden = true;
        setPageStep("review");
    });

    reviewSubmitButton.addEventListener("click", async () => {
        if (requestSubmitted || reviewSubmitButton.disabled) return;
        const requestData = new FormData(form);
        requestData.set("length", String(Number(lengthInput.value)));
        requestData.set("width", String(Number(widthInput.value)));
        requestData.set("customFrameRequest", customFrameInput.value.trim());
        if (selectedFile) {
            requestData.set("referenceImage", selectedFile);
        } else {
            requestData.delete("referenceImage");
        }

        reviewSubmitButton.disabled = true;
        reviewSubmitButton.setAttribute("aria-busy", "true");
        reviewFeedback.textContent = "Submitting your request for admin review...";
        reviewFeedback.classList.remove("is-error");
        reviewFeedback.hidden = false;
        try {
            const response = await fetch("/api/v1/custom-orders", {
                method: "POST",
                body: requestData
            });
            const result = await response.json();
            if (!response.ok) {
                reviewFeedback.textContent = result.message ||
                    "Your request could not be submitted. Check your details and try again.";
                reviewFeedback.classList.add("is-error");
                return;
            }

            const submittedAt = new Date();
            const selectedFrame = form.querySelector('input[name="frame"]:checked');
            document.getElementById("confirmation-request-id").textContent = result.orderId;
            document.getElementById("confirmation-submitted-date").textContent =
                submittedAt.toLocaleString();
            document.getElementById("confirmation-dimensions").textContent =
                `${lengthInput.value} (H) × ${widthInput.value} (W) ${unitSelect.value}`;
            document.getElementById("confirmation-frame").textContent = selectedFrame.value;

            const customFrameRow = document.getElementById("confirmation-custom-frame-row");
            customFrameRow.hidden = selectedFrame.value !== "Custom (Request)";
            document.getElementById("confirmation-custom-frame").textContent =
                customFrameInput.value.trim();

            const referenceRow = document.getElementById("confirmation-reference-row");
            const confirmationImage = document.getElementById("confirmation-reference-image");
            referenceRow.hidden = !selectedFile;
            if (selectedFile && referenceImageUrl) {
                confirmationImage.src = referenceImageUrl;
            } else {
                confirmationImage.removeAttribute("src");
            }

            const message = messageInput.value.trim();
            document.getElementById("confirmation-message-row").hidden = !message;
            document.getElementById("confirmation-message").textContent = message;
            requestSubmitted = true;
            setPageStep("submitted");
        } catch (error) {
            console.error("Unable to submit the custom order request.", error);
            reviewFeedback.textContent =
                "We couldn’t reach the server to save your request. Please try again.";
            reviewFeedback.classList.add("is-error");
        } finally {
            reviewSubmitButton.disabled = false;
            reviewSubmitButton.removeAttribute("aria-busy");
        }
    });
    checkoutReceipt.addEventListener("change", () => {
        const file = checkoutReceipt.files[0];
        if (!file) {
            checkoutReceiptName.textContent = "No file selected";
            checkoutError.hidden = true;
            return;
        }
        const validType = ["image/jpeg", "image/png", "application/pdf"].includes(file.type) ||
            /\.(jpe?g|png|pdf)$/i.test(file.name);
        if (!validType || file.size > maxFileSize) {
            checkoutReceipt.value = "";
            checkoutReceiptName.textContent = "No file selected";
            checkoutError.textContent = validType
                ? "The sample receipt must be 10MB or smaller."
                : "Choose a JPG, PNG, or PDF sample receipt.";
            checkoutError.hidden = false;
            return;
        }
        checkoutError.hidden = true;
        checkoutReceiptName.textContent = file.name;
    });

    checkoutForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (checkoutCompleted || checkoutSubmitButton.disabled) return;
        checkoutError.hidden = true;
        checkoutFeedback.hidden = true;
        if (!checkoutForm.reportValidity()) return;

        const requestData = new FormData(form);
        requestData.set("length", String(Number(lengthInput.value)));
        requestData.set("width", String(Number(widthInput.value)));
        requestData.set("customFrameRequest", customFrameInput.value.trim());
        if (!selectedFile) requestData.delete("referenceImage");
        new FormData(checkoutForm).forEach((value, key) => requestData.set(key, value));

        checkoutSubmitButton.disabled = true;
        checkoutSubmitButton.setAttribute("aria-busy", "true");
        checkoutFeedback.textContent = "Submitting demo checkout...";
        checkoutFeedback.classList.remove("is-error");
        checkoutFeedback.hidden = false;
        try {
            const response = await fetch("/api/v1/custom-orders/checkout", {
                method: "POST",
                body: requestData
            });
            const result = await response.json();
            if (!response.ok) {
                checkoutFeedback.textContent = result.message ||
                    "The demo checkout could not be submitted. Check your details and try again.";
                checkoutFeedback.classList.add("is-error");
                return;
            }
            checkoutFeedback.textContent =
                `Demo order ${result.orderId} was recorded. QR payment is not verified by this site.`;
            checkoutCompleted = true;
            checkoutSubmitButton.textContent = "Demo Order Recorded";
            editButtons.forEach((button) => {
                button.disabled = true;
            });
            document.getElementById("back-to-review").disabled = true;
            checkoutForm.querySelectorAll("input, textarea").forEach((input) => {
                input.disabled = true;
            });
        } catch (error) {
            console.error("Unable to submit the demo checkout.", error);
            checkoutFeedback.textContent =
                "We couldn’t reach the server to record your demo checkout. Please try again.";
            checkoutFeedback.classList.add("is-error");
        } finally {
            checkoutSubmitButton.disabled = checkoutCompleted;
            checkoutSubmitButton.removeAttribute("aria-busy");
        }
    });
})();
