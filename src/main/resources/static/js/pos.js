(() => {
    const root = document.getElementById("pos-order-list");
    if (!root || !window.StyleMariaApi) return;

    const api = window.StyleMariaApi;
    const currency = new Intl.NumberFormat("en-PH", {
        style: "currency",
        currency: "PHP",
        minimumFractionDigits: 2
    });
    const search = document.getElementById("pos-search");
    const statusFilter = document.getElementById("pos-status-filter");
    const feedback = document.getElementById("pos-feedback");
    const orderForm = document.getElementById("pos-order-form");
    const orderDialog = document.getElementById("pos-order-dialog");
    const receiptDialog = document.getElementById("pos-receipt-dialog");
    const amountPaidInput = document.getElementById("pos-amount-paid");
    const paymentReferenceInput = document.getElementById("pos-payment-reference");
    const paymentReferenceLabel = document.getElementById("pos-payment-reference-label");
    const processPaymentButton = document.getElementById("process-pos-payment");
    const paidMethod = () => document.querySelector('input[name="pos-payment-method"]:checked').value;
    let orders = [];
    let activeOrderId = null;

    const message = (text, isError = false) => {
        feedback.textContent = text;
        feedback.classList.toggle("is-error", isError);
        feedback.classList.toggle("is-success", !isError && text.length > 0);
    };
    const orderTotal = (order) => Number(order.amount ?? (order.priceCents / 100));
    const statusText = (status) => status.replaceAll("_", " ")
        .toLocaleLowerCase()
        .replace(/\b\w/g, (letter) => letter.toLocaleUpperCase());
    const activeOrder = () => orders.find((order) => order.id === activeOrderId
        && order.paymentStatus !== "PAID" && order.status !== "COMPLETED");
    const isOpenOrder = (order) => order.paymentStatus !== "PAID" && order.status !== "COMPLETED";
    const setText = (id, value) => {
        document.getElementById(id).textContent = value;
    };

    const renderCart = () => {
        const order = activeOrder();
        const hasOrder = Boolean(order);
        document.getElementById("pos-no-customer").hidden = hasOrder;
        document.getElementById("pos-customer-info").hidden = !hasOrder;
        document.getElementById("pos-no-items").hidden = hasOrder;
        document.getElementById("pos-cart-line").hidden = !hasOrder;
        document.getElementById("cancel-pos-cart").disabled = !hasOrder;
        processPaymentButton.disabled = !hasOrder;

        if (!order) {
            setText("pos-total", currency.format(0));
            setText("pos-change", currency.format(0));
            amountPaidInput.value = "";
            amountPaidInput.disabled = true;
            paymentReferenceInput.value = "";
            paymentReferenceInput.hidden = true;
            paymentReferenceLabel.hidden = true;
            return;
        }

        const total = orderTotal(order);
        setText("pos-customer-name", order.customerName);
        setText("pos-customer-contact", order.contact || "Not provided");
        setText("pos-customer-address", order.address || "Not provided");
        setText("pos-cart-description", `${order.id} — ${order.item}`);
        setText("pos-cart-price", currency.format(total));
        setText("pos-total", currency.format(total));
        amountPaidInput.disabled = paidMethod() !== "cash";
        updateChangeDue();
    };

    const updateChangeDue = () => {
        const order = activeOrder();
        const isCash = paidMethod() === "cash";
        const total = order ? Math.round(orderTotal(order) * 100) : 0;
        const tendered = Math.round(Number(amountPaidInput.value) * 100);
        const valid = isCash && Number.isSafeInteger(tendered) && tendered >= total;
        document.querySelector(".pos-change-row").hidden = !isCash;
        const showReference = Boolean(order) && !isCash;
        paymentReferenceInput.hidden = !showReference;
        paymentReferenceLabel.hidden = !showReference;
        setText("pos-change", currency.format(valid ? (tendered - total) / 100 : 0));
        amountPaidInput.disabled = !order || !isCash;
        processPaymentButton.disabled = !order || (isCash && !valid);
        processPaymentButton.textContent = isCash
            ? "Record payment & print"
            : "Record GCash / Maya payment & print";
    };

    const renderOrders = () => {
        const openOrders = orders.filter((order) => order.status !== "COMPLETED"
            && order.paymentStatus !== "PAID");
        const query = search.value.trim().toLocaleLowerCase();
        const selectedStatus = statusFilter.value;
        const matches = orders.filter((order) => {
            const isOpen = order.status !== "COMPLETED" && order.paymentStatus !== "PAID";
            const matchesStatus = selectedStatus === "all"
                || (selectedStatus === "open" && isOpen)
                || (selectedStatus === "completed" && !isOpen);
            return matchesStatus && `${order.id} ${order.customerName} ${order.contact || ""} ${order.item}`
                .toLocaleLowerCase().includes(query);
        });
        root.replaceChildren();

        matches.forEach((order) => {
            const card = document.createElement("article");
            card.className = "pos-order-card";
            card.classList.toggle("is-in-cart", order.id === activeOrderId);
            const heading = document.createElement("div");
            heading.className = "pos-order-card-heading";
            const id = document.createElement("strong");
            id.textContent = order.id;
            const customer = document.createElement("span");
            customer.textContent = order.customerName;
            heading.append(id, customer);

            const item = document.createElement("p");
            item.className = "pos-order-item";
            item.textContent = order.item;
            const summary = document.createElement("div");
            summary.className = "pos-order-summary";
            const status = document.createElement("span");
            status.className = "pos-order-status";
            status.textContent = statusText(order.status);
            const total = document.createElement("strong");
            total.textContent = currency.format(orderTotal(order));
            summary.append(status, total);

            const actions = document.createElement("div");
            actions.className = "pos-order-card-actions";
            if (isOpenOrder(order)) {
                const select = document.createElement("button");
                select.type = "button";
                select.className = "pos-add-button";
                select.dataset.selectOrder = order.id;
                select.disabled = order.id === activeOrderId;
                select.textContent = order.id === activeOrderId ? "In active cart" : "Select order";
                actions.append(select);
            } else {
                const payment = document.createElement("span");
                payment.textContent = `Paid via ${order.paymentMethod || "payment"}`;
                actions.append(payment);
            }
            const nextStatus = isOpenOrder(order) && (order.status === "PENDING" ? "FOR_CONFIRMATION"
                : order.status === "FOR_CONFIRMATION" ? "IN_PRODUCTION" : null);
            if (nextStatus) {
                const advance = document.createElement("button");
                advance.type = "button";
                advance.className = "pos-cancel-button";
                advance.dataset.advanceOrder = order.id;
                advance.dataset.nextStatus = nextStatus;
                advance.textContent = nextStatus === "FOR_CONFIRMATION"
                    ? "Move to confirmation"
                    : "Start production";
                actions.append(advance);
            }
            card.append(heading, item, summary, actions);
            root.append(card);
        });

        setText("pending-orders-count", `(${matches.length})`);
        const emptyState = document.getElementById("pos-empty-state");
        emptyState.hidden = matches.length > 0;
        emptyState.textContent = statusFilter.value === "completed"
            ? "No completed business orders match your search."
            : openOrders.length === 0
                ? "No open business orders. Add an order to begin."
                : "No business orders match your filters.";
        renderCart();
    };

    const loadOrders = async () => {
        message("Loading business orders…");
        try {
            orders = await api.request("/api/v1/orders");
            if (!orders.some((order) => order.id === activeOrderId)) activeOrderId = null;
            renderOrders();
            message("");
        } catch (error) {
            message(`Business orders could not be loaded: ${error.message}`, true);
        }
    };

    const showReceipt = (order, tenderedAmount, paymentMethod) => {
        setText("receipt-id", `RCT-${order.id}`);
        setText("receipt-date", order.paidAt
            ? new Date(order.paidAt).toLocaleString()
            : new Date().toLocaleString());
        setText("receipt-customer", order.customerName);
        setText("receipt-item", `${order.id} — ${order.item}`);
        setText("receipt-method", paymentMethod === "CASH" ? "Cash" : paymentMethod);
        setText("receipt-total", currency.format(orderTotal(order)));
        setText("receipt-paid", currency.format(tenderedAmount));
        setText("receipt-change", currency.format(Math.max(0, tenderedAmount - orderTotal(order))));
        receiptDialog.showModal();
    };

    const now = new Date();
    const localDate = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    document.getElementById("pos-date").textContent = new Intl.DateTimeFormat(undefined, {
        day: "numeric",
        month: "long",
        year: "numeric"
    }).format(now);
    document.getElementById("pos-date").dateTime = localDate.toISOString().slice(0, 10);

    document.getElementById("add-pos-order").addEventListener("click", () => {
        orderForm.reset();
        orderDialog.showModal();
        document.getElementById("new-customer-name").focus();
    });
    document.getElementById("close-pos-order-dialog").addEventListener("click", () => orderDialog.close());
    document.getElementById("cancel-pos-order").addEventListener("click", () => orderDialog.close());
    document.getElementById("new-order-price").addEventListener("input", (event) => {
        event.currentTarget.setCustomValidity("");
    });
    orderDialog.addEventListener("click", (event) => {
        if (event.target === orderDialog) orderDialog.close();
    });

    orderForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!orderForm.reportValidity()) return;
        const data = new FormData(orderForm);
        const amount = Number(Number(data.get("price")).toFixed(2));
        if (!Number.isFinite(amount) || amount <= 0) {
            document.getElementById("new-order-price").setCustomValidity("Enter a valid amount greater than zero.");
            document.getElementById("new-order-price").reportValidity();
            return;
        }
        const submit = orderForm.querySelector('[type="submit"]');
        submit.disabled = true;
        try {
            const created = await api.request("/api/v1/orders", {
                method: "POST",
                body: JSON.stringify({
                    customerName: String(data.get("customerName")).trim(),
                    contact: String(data.get("contact")).trim() || null,
                    address: String(data.get("address")).trim() || null,
                    item: String(data.get("item")).trim(),
                    amount
                })
            });
            orderDialog.close();
            search.value = "";
            activeOrderId = created.id;
            await loadOrders();
            message(`${created.id} was saved to the server and is ready in the active cart.`);
        } catch (error) {
            message(`Order could not be created: ${error.message}`, true);
        } finally {
            submit.disabled = false;
        }
    });

    root.addEventListener("click", async (event) => {
        const advance = event.target.closest("[data-advance-order]");
        if (advance) {
            advance.disabled = true;
            try {
                await api.request(`/api/v1/orders/${encodeURIComponent(advance.dataset.advanceOrder)}/status`, {
                    method: "PATCH",
                    body: JSON.stringify({ status: advance.dataset.nextStatus })
                });
                await loadOrders();
                message(`Order moved to ${statusText(advance.dataset.nextStatus)}.`);
            } catch (error) {
                message(`Order status could not be changed: ${error.message}`, true);
                advance.disabled = false;
            }
            return;
        }
        const select = event.target.closest("[data-select-order]");
        if (!select) return;
        activeOrderId = select.dataset.selectOrder;
        renderOrders();
    });
    search.addEventListener("input", renderOrders);
    statusFilter.addEventListener("change", renderOrders);
    document.querySelectorAll('input[name="pos-payment-method"]').forEach((input) => {
        input.addEventListener("change", updateChangeDue);
    });
    amountPaidInput.addEventListener("input", updateChangeDue);
    document.getElementById("cancel-pos-cart").addEventListener("click", () => {
        activeOrderId = null;
        renderOrders();
    });
    processPaymentButton.addEventListener("click", async () => {
        const order = activeOrder();
        if (!order) return;
        const selectedMethod = paidMethod();
        const paymentMethod = selectedMethod === "cash" ? "CASH" : selectedMethod.toUpperCase();
        const tenderedAmount = paymentMethod === "CASH"
            ? Number(Number(amountPaidInput.value).toFixed(2))
            : orderTotal(order);
        processPaymentButton.disabled = true;
        try {
            const paid = await api.request(`/api/v1/orders/${encodeURIComponent(order.id)}/payments`, {
                method: "POST",
                body: JSON.stringify({
                    paymentMethod,
                    amountPaid: tenderedAmount,
                    reference: paymentReferenceInput.value.trim() || null
                })
            });
            showReceipt(paid, tenderedAmount, paymentMethod);
            activeOrderId = null;
            await loadOrders();
            message(`${order.id} payment recorded on the server.`);
        } catch (error) {
            message(`Payment was not recorded: ${error.message}`, true);
            updateChangeDue();
        }
    });
    document.getElementById("close-pos-receipt").addEventListener("click", () => receiptDialog.close());
    document.getElementById("print-pos-receipt").addEventListener("click", () => window.print());
    receiptDialog.addEventListener("click", (event) => {
        if (event.target === receiptDialog) receiptDialog.close();
    });

    loadOrders();
})();
