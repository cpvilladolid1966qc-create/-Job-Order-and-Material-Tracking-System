(() => {
    const tableBody = document.getElementById("materials-body");
    if (!tableBody || !window.StyleMariaApi) return;

    const api = window.StyleMariaApi;
    const categoryFilter = document.getElementById("category-filter");
    const statusFilter = document.getElementById("material-status-filter");
    const searchInput = document.getElementById("material-search");
    const feedback = document.getElementById("inventory-feedback");
    const catalogPermission = document.getElementById("inventory-catalog-permission");
    const materialForm = document.getElementById("material-form");
    const categoryForm = document.getElementById("category-form");
    const movementForm = document.getElementById("movement-form");
    const materialsEmpty = document.getElementById("materials-empty");
    const materialCount = document.getElementById("materials-count");
    const historyBody = document.getElementById("inventory-history-body");
    const historyEmpty = document.getElementById("inventory-history-empty");
    const categoryList = document.getElementById("inventory-category-list");
    const materialSelect = document.getElementById("movement-material");
    const categorySelect = document.getElementById("material-category");
    const unitSelect = document.getElementById("material-unit");
    const adjustmentDirection = document.getElementById("adjustment-direction");
    const adjustmentSelect = document.getElementById("movement-direction");
    const materialIdInput = document.getElementById("material-id");
    const materialSubmit = document.getElementById("material-submit");
    const categoryIdInput = document.getElementById("category-id");
    const categoryNameInput = document.getElementById("new-category-name");
    const categorySubmit = document.getElementById("category-submit");
    let materials = [];
    let categories = [];
    let units = [];
    let movements = [];
    let canManageCatalog = false;

    const setFeedback = (message, isError = false) => {
        feedback.textContent = message;
        feedback.classList.toggle("is-error", isError);
        feedback.classList.toggle("is-success", !isError && message.length > 0);
    };
    const dateTime = (value) => value
        ? new Date(value).toLocaleString()
        : "—";
    const quantity = (value) => Number(value).toLocaleString(undefined, {
        maximumFractionDigits: 3
    });
    const statusLabel = (material) => !material.active
        ? "Inactive"
        : material.lowStock ? "Low Stock" : "In Stock";
    const updateCatalogPermission = (message) => {
        [...materialForm.elements, ...categoryForm.elements].forEach((control) => {
            control.disabled = !canManageCatalog;
        });
        catalogPermission.hidden = canManageCatalog || !message;
        catalogPermission.textContent = message || "";
        catalogPermission.classList.toggle("is-error", !canManageCatalog && Boolean(message));
    };
    updateCatalogPermission("Checking account permissions…");

    const renderSelectors = () => {
        const selectedCategory = categorySelect.value;
        const selectedUnit = unitSelect.value;
        const selectedMaterial = materialSelect.value;

        categorySelect.replaceChildren(new Option("Select category", ""));
        categories.filter((category) => category.active).forEach((category) => {
            categorySelect.add(new Option(category.name, String(category.id)));
        });
        if ([...categorySelect.options].some((option) => option.value === selectedCategory)) {
            categorySelect.value = selectedCategory;
        }

        unitSelect.replaceChildren(new Option("Select unit", ""));
        units.filter((unit) => unit.active).forEach((unit) => {
            unitSelect.add(new Option(`${unit.displayName} (${unit.code})`, unit.code));
        });
        if ([...unitSelect.options].some((option) => option.value === selectedUnit)) {
            unitSelect.value = selectedUnit;
        }

        materialSelect.replaceChildren(new Option("Select material", ""));
        materials.forEach((material) => {
            const inactive = material.active ? "" : " — Inactive";
            materialSelect.add(new Option(`${material.name}${inactive} (${material.quantityOnHand} ${material.unitCode})`,
                String(material.id)));
        });
        if ([...materialSelect.options].some((option) => option.value === selectedMaterial)) {
            materialSelect.value = selectedMaterial;
        }

        const currentFilter = categoryFilter.value;
        categoryFilter.replaceChildren(new Option("All Categories", "all"));
        categories.forEach((category) => {
            categoryFilter.add(new Option(category.name, String(category.id)));
        });
        if ([...categoryFilter.options].some((option) => option.value === currentFilter)) {
            categoryFilter.value = currentFilter;
        }
    };

    const renderMaterials = () => {
        const query = searchInput.value.trim().toLocaleLowerCase();
        const selectedCategory = categoryFilter.value;
        const selectedStatus = statusFilter.value;
        const visible = materials.filter((material) => {
            const matchesSearch = `${material.name} ${material.sku || ""} ${material.categoryName} ${material.unitName}`
                .toLocaleLowerCase().includes(query);
            const matchesCategory = selectedCategory === "all" ||
                String(material.categoryId) === selectedCategory;
            const matchesStatus = selectedStatus === "all"
                || (selectedStatus === "active" && material.active)
                || (selectedStatus === "inactive" && !material.active)
                || (selectedStatus === "low-stock" && material.active && material.lowStock)
                || (selectedStatus === "in-stock" && material.active && !material.lowStock);
            return matchesSearch && matchesCategory && matchesStatus;
        });

        tableBody.replaceChildren();
        visible.forEach((material) => {
            const row = document.createElement("tr");
            const values = [
                `${material.name}${material.sku ? ` (${material.sku})` : ""}`,
                material.categoryName,
                quantity(material.quantityOnHand),
                material.unitName,
                statusLabel(material),
                dateTime(material.updatedAt)
            ];
            values.forEach((value, index) => {
                const cell = document.createElement("td");
                cell.textContent = value;
                if (index === 0) cell.className = "material-name";
                if (index === 2) cell.className = "material-quantity";
                if (index === 4) {
                    cell.className = `material-status${material.lowStock && material.active ? " low" : ""}`;
                }
                row.append(cell);
            });

            const actions = document.createElement("td");
            const editButton = document.createElement("button");
            editButton.type = "button";
            editButton.className = "dashboard-button dashboard-button-secondary";
            editButton.dataset.editMaterial = String(material.id);
            editButton.textContent = "Edit";
            editButton.disabled = !canManageCatalog;
            const statusButton = document.createElement("button");
            statusButton.type = "button";
            statusButton.className = "dashboard-button dashboard-button-secondary";
            statusButton.dataset.toggleMaterial = String(material.id);
            statusButton.textContent = material.active ? "Deactivate" : "Activate";
            statusButton.disabled = !canManageCatalog;
            actions.append(editButton, statusButton);
            row.append(actions);
            tableBody.append(row);
        });

        materialsEmpty.hidden = visible.length > 0;
        materialCount.textContent = `Showing ${visible.length} of ${materials.length} materials`;
        renderSelectors();
    };

    const renderHistory = () => {
        historyBody.replaceChildren();
        movements.forEach((movement) => {
            const row = document.createElement("tr");
            [
                dateTime(movement.occurredAt),
                movement.materialName,
                movement.type,
                quantity(movement.quantityDelta),
                quantity(movement.balanceAfter),
                movement.reason,
                `${movement.actorEmail} (${movement.actorRole})`,
                movement.orderId || "—"
            ].forEach((value) => {
                const cell = document.createElement("td");
                cell.textContent = value;
                row.append(cell);
            });
            historyBody.append(row);
        });
        historyEmpty.hidden = movements.length > 0;
    };

    const renderCategoryList = () => {
        categoryList.replaceChildren();
        categories.forEach((category) => {
            const row = document.createElement("li");
            const name = document.createElement("span");
            name.textContent = `${category.name}${category.active ? "" : " (Inactive)"}`;
            const edit = document.createElement("button");
            edit.type = "button";
            edit.className = "dashboard-button dashboard-button-secondary";
            edit.dataset.editCategory = String(category.id);
            edit.textContent = "Edit";
            const toggle = document.createElement("button");
            toggle.type = "button";
            toggle.className = "dashboard-button dashboard-button-secondary";
            toggle.dataset.toggleCategory = String(category.id);
            toggle.dataset.active = String(!category.active);
            toggle.textContent = category.active ? "Deactivate" : "Activate";
            edit.disabled = !canManageCatalog;
            toggle.disabled = !canManageCatalog;
            row.append(name, edit, toggle);
            categoryList.append(row);
        });
    };

    const loadInventory = async () => {
        setFeedback("Loading inventory…");
        try {
            const [materialRows, categoryRows, unitRows, movementRows, session] = await Promise.all([
                api.request("/api/v1/inventory/materials?includeInactive=true"),
                api.request("/api/v1/inventory/categories"),
                api.request("/api/v1/inventory/units"),
                api.request("/api/v1/inventory/movements"),
                api.request("/api/v1/session")
            ]);
            if (![materialRows, categoryRows, unitRows, movementRows].every(Array.isArray)) {
                throw new Error("The inventory API returned an invalid response.");
            }
            materials = materialRows;
            categories = categoryRows;
            units = unitRows;
            movements = movementRows;
            canManageCatalog = session.role === "ADMIN";
            updateCatalogPermission(canManageCatalog
                ? ""
                : "Only an admin account can add or edit categories and materials. Staff can record stock movements.");
            renderSelectors();
            renderCategoryList();
            renderMaterials();
            renderHistory();
            setFeedback("");
        } catch (error) {
            canManageCatalog = false;
            updateCatalogPermission(`Catalog permissions could not be verified: ${error.message}`);
            setFeedback(`Inventory could not be loaded: ${error.message}`, true);
        }
    };

    const resetMaterialForm = () => {
        materialForm.reset();
        materialIdInput.value = "";
        document.getElementById("material-editor-title").textContent = "Add material";
        materialSubmit.textContent = "Create material";
    };

    materialForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!materialForm.reportValidity()) return;
        const formData = new FormData(materialForm);
        const id = materialIdInput.value;
        const payload = {
            name: String(formData.get("name")).trim(),
            sku: String(formData.get("sku")).trim() || null,
            categoryId: Number(formData.get("categoryId")),
            unitCode: String(formData.get("unitCode")),
            lowStockThreshold: Number(formData.get("lowStockThreshold") || 0),
            active: document.getElementById("material-active").checked
        };
        try {
            const path = id
                ? `/api/v1/inventory/materials/${encodeURIComponent(id)}`
                : "/api/v1/inventory/materials";
            await api.request(path, {
                method: id ? "PATCH" : "POST",
                body: JSON.stringify(payload)
            });
            resetMaterialForm();
            await loadInventory();
            setFeedback(id ? "Material updated." : "Material added. Initial stock is zero; record a receipt to add quantity.");
        } catch (error) {
            setFeedback(`Material could not be saved: ${error.message}`, true);
        }
    });

    categoryForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!categoryForm.reportValidity()) return;
        const categoryId = categoryIdInput.value;
        const name = categoryNameInput.value.trim();
        try {
            await api.request(categoryId
                ? `/api/v1/inventory/categories/${encodeURIComponent(categoryId)}`
                : "/api/v1/inventory/categories", {
                method: categoryId ? "PATCH" : "POST",
                body: JSON.stringify({ name })
            });
            categoryForm.reset();
            categoryIdInput.value = "";
            document.getElementById("category-title").textContent = "Add category";
            categorySubmit.textContent = "Create category";
            await loadInventory();
            setFeedback(categoryId ? "Category updated." : "Category added.");
        } catch (error) {
            setFeedback(`Category could not be saved: ${error.message}`, true);
        }
    });

    movementForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!movementForm.reportValidity()) return;
        const formData = new FormData(movementForm);
        const movement = {
            materialId: Number(formData.get("materialId")),
            type: String(formData.get("type")),
            quantity: Number(formData.get("quantity")),
            reason: String(formData.get("reason")).trim(),
            adjustmentDirection: String(formData.get("adjustmentDirection") || ""),
            orderId: String(formData.get("orderId")).trim() || null
        };
        try {
            await api.request("/api/v1/inventory/movements", {
                method: "POST",
                body: JSON.stringify(movement)
            });
            movementForm.reset();
            adjustmentDirection.hidden = true;
            adjustmentSelect.hidden = true;
            await loadInventory();
            setFeedback("Stock movement recorded.");
        } catch (error) {
            setFeedback(`Stock movement was not recorded: ${error.message}`, true);
        }
    });

    tableBody.addEventListener("click", async (event) => {
        const editButton = event.target.closest("[data-edit-material]");
        const toggleButton = event.target.closest("[data-toggle-material]");
        if (editButton) {
            const material = materials.find((item) => String(item.id) === editButton.dataset.editMaterial);
            if (!material) return;
            materialIdInput.value = String(material.id);
            materialForm.elements.name.value = material.name;
            materialForm.elements.sku.value = material.sku || "";
            categorySelect.value = String(material.categoryId);
            unitSelect.value = material.unitCode;
            materialForm.elements.lowStockThreshold.value = material.lowStockThreshold;
            document.getElementById("material-active").checked = material.active;
            document.getElementById("material-editor-title").textContent = "Edit material";
            materialSubmit.textContent = "Save changes";
            materialForm.scrollIntoView({ behavior: "smooth", block: "center" });
            return;
        }
        if (toggleButton) {
            const material = materials.find((item) => String(item.id) === toggleButton.dataset.toggleMaterial);
            if (!material) return;
            try {
                await api.request(`/api/v1/inventory/materials/${material.id}`, {
                    method: "PATCH",
                    body: JSON.stringify({ active: !material.active })
                });
                await loadInventory();
                setFeedback(material.active ? "Material deactivated." : "Material activated.");
            } catch (error) {
                setFeedback(`Material status could not be changed: ${error.message}`, true);
            }
        }
    });

    categoryList.addEventListener("click", async (event) => {
        const editButton = event.target.closest("[data-edit-category]");
        const toggleButton = event.target.closest("[data-toggle-category]");
        if (editButton) {
            const category = categories.find((item) => String(item.id) === editButton.dataset.editCategory);
            if (!category) return;
            categoryIdInput.value = String(category.id);
            categoryNameInput.value = category.name;
            document.getElementById("category-title").textContent = "Edit category";
            categorySubmit.textContent = "Save category";
            categoryForm.scrollIntoView({ behavior: "smooth", block: "center" });
            return;
        }
        if (toggleButton) {
            try {
                await api.request(`/api/v1/inventory/categories/${encodeURIComponent(toggleButton.dataset.toggleCategory)}`, {
                    method: "PATCH",
                    body: JSON.stringify({ active: toggleButton.dataset.active === "true" })
                });
                await loadInventory();
                setFeedback(toggleButton.dataset.active === "true" ? "Category activated." : "Category deactivated.");
            } catch (error) {
                setFeedback(`Category status could not be changed: ${error.message}`, true);
            }
        }
    });

    document.getElementById("cancel-material-edit").addEventListener("click", resetMaterialForm);
    document.getElementById("cancel-category-edit").addEventListener("click", () => {
        categoryForm.reset();
        categoryIdInput.value = "";
        document.getElementById("category-title").textContent = "Add category";
        categorySubmit.textContent = "Create category";
    });
    document.getElementById("show-inventory-history").addEventListener("click", async () => {
        try {
            const selectedMaterial = materialSelect.value;
            const path = selectedMaterial
                ? `/api/v1/inventory/movements?materialId=${encodeURIComponent(selectedMaterial)}`
                : "/api/v1/inventory/movements";
            movements = await api.request(path);
            renderHistory();
        } catch (error) {
            setFeedback(`Movement history could not be loaded: ${error.message}`, true);
        }
    });
    searchInput.addEventListener("input", renderMaterials);
    categoryFilter.addEventListener("change", renderMaterials);
    statusFilter.addEventListener("change", renderMaterials);
    document.getElementById("movement-type").addEventListener("change", (event) => {
        adjustmentDirection.hidden = event.currentTarget.value !== "ADJUSTMENT";
        adjustmentSelect.hidden = event.currentTarget.value !== "ADJUSTMENT";
    });

    loadInventory();
})();
