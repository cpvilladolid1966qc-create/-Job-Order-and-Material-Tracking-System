(() => {
    const toast = document.getElementById("dashboard-toast");
    let toastTimeout;

    const showToast = (message) => {
        if (!toast) return;
        toast.textContent = message;
        toast.classList.add("is-visible");
        window.clearTimeout(toastTimeout);
        toastTimeout = window.setTimeout(() => {
            toast.classList.remove("is-visible");
        }, 2800);
    };

    const togglePasswordVisibility = (button, input) => {
        const showPassword = input.type === "password";
        input.type = showPassword ? "text" : "password";
        button.setAttribute("aria-label", showPassword ? "Hide password" : "Show password");
        button.setAttribute("aria-pressed", String(showPassword));
    };

    document.querySelectorAll("[data-toggle-password]").forEach((button) => {
        button.addEventListener("click", () => {
            togglePasswordVisibility(
                button,
                document.getElementById(button.dataset.togglePassword)
            );
        });
    });

    const loginForm = document.querySelector("#sign-in-panel form");
    if (loginForm) {
        const query = new URLSearchParams(window.location.search);
        const loginError = document.getElementById("login-error");
        const loginUnverified = document.getElementById("login-unverified");
        const verificationInvalid = document.getElementById("verification-invalid");
        const loginUnauthorized = document.getElementById("login-unauthorized");
        const loginLogout = document.getElementById("login-logout");
        const signupSuccess = document.getElementById("signup-success");
        const emailVerifiedSuccess = document.getElementById("email-verified-success");
        const passwordResetSuccess = document.getElementById("password-reset-success");
        const signupError = document.getElementById("signup-error");
        const signInPanel = document.getElementById("sign-in-panel");
        const signUpPanel = document.getElementById("sign-up-panel");
        const signInTab = document.getElementById("show-sign-in");
        const signUpTab = document.getElementById("show-sign-up");

        loginError.hidden = query.get("error") !== "invalid";
        loginUnverified.hidden = query.get("error") !== "unverified";
        verificationInvalid.hidden = query.get("verificationError") !== "invalid";
        loginUnauthorized.hidden = query.get("error") !== "unauthorized";
        loginLogout.hidden = !query.has("logout");
        signupSuccess.hidden = !query.has("verificationSent");
        emailVerifiedSuccess.hidden = !query.has("verified");
        passwordResetSuccess.hidden = !query.has("passwordReset");
        signupError.hidden = !query.has("signupError");
        if (query.get("signupError") === "exists") {
            signupError.textContent = "An account with that email already exists. Sign in or use another email.";
        } else if (query.get("signupError") === "invalid") {
            signupError.textContent = "Enter your name and a valid email, choose a password between 12 and 128 characters, and make sure both password fields match.";
        } else if (query.get("signupError") === "delivery") {
            signupError.textContent = "We couldn’t send the verification email. Check your email service settings or try again later.";
        }

        const setAuthTab = (showSignUp) => {
            signInPanel.hidden = showSignUp;
            signUpPanel.hidden = !showSignUp;
            signInTab.classList.toggle("is-active", !showSignUp);
            signUpTab.classList.toggle("is-active", showSignUp);
            signInTab.setAttribute("aria-selected", String(!showSignUp));
            signUpTab.setAttribute("aria-selected", String(showSignUp));
            if (showSignUp) document.getElementById("signup-name").focus();
        };
        signInTab.addEventListener("click", () => setAuthTab(false));
        signUpTab.addEventListener("click", () => setAuthTab(true));
        if (query.has("signupError")) setAuthTab(true);

        const passwordInput = document.getElementById("password");
        const passwordVisibilityToggle = document.getElementById("password-visibility-toggle");
        passwordVisibilityToggle.addEventListener("click", () => {
            togglePasswordVisibility(passwordVisibilityToggle, passwordInput);
        });
    }

    const forgotPasswordForm = document.getElementById("forgot-password-form");
    if (forgotPasswordForm) {
        const query = new URLSearchParams(window.location.search);
        document.getElementById("recovery-requested").hidden = !query.has("requested");
        document.getElementById("recovery-delivery-error").hidden = !query.has("deliveryError");
    }

    const verificationForm = document.getElementById("verification-resend-form");
    if (verificationForm) {
        const query = new URLSearchParams(window.location.search);
        const token = query.get("token");
        const confirmationForm = document.getElementById("verification-confirm-form");
        confirmationForm.hidden = !token;
        if (token) {
            document.getElementById("verification-token").value = token;
            verificationForm.hidden = true;
            document.getElementById("verification-resend-title").hidden = true;
            document.getElementById("verification-resend-description").hidden = true;
        }
        document.getElementById("verification-sent").hidden = !query.has("sent");
        document.getElementById("verification-delivery-error").hidden = !query.has("deliveryError");
        document.getElementById("verification-invalid").hidden = !query.has("error");
    }

    const resetPasswordForm = document.getElementById("reset-password-form");
    if (resetPasswordForm) {
        const query = new URLSearchParams(window.location.search);
        const token = query.get("token");
        const error = document.getElementById("reset-password-error");
        document.getElementById("reset-token").value = token || "";
        if (query.get("error") === "expired") {
            error.textContent = "This reset link is invalid, expired, or already used. Request a new link.";
            error.hidden = false;
        } else if (query.get("error") === "invalid") {
            error.textContent = "Choose a password with at least 12 characters and make sure both fields match.";
            error.hidden = false;
        } else if (!token) {
            error.textContent = "This reset link is missing its token. Request a new reset email.";
            error.hidden = false;
            resetPasswordForm.querySelector('button[type="submit"]').disabled = true;
        }

        resetPasswordForm.addEventListener("submit", (event) => {
            const password = document.getElementById("new-password");
            const confirmation = document.getElementById("confirm-new-password");
            if (password.value.length < 12 || password.value !== confirmation.value) {
                event.preventDefault();
                error.textContent = password.value.length < 12
                    ? "Choose a password with at least 12 characters."
                    : "The passwords do not match.";
                error.hidden = false;
            }
        });
    }

    const navigation = document.getElementById("workshop-navigation");
    const menuButton = document.querySelector(".workshop-menu-button");

    if (navigation && menuButton) {
        menuButton.addEventListener("click", () => {
            const isOpen = navigation.classList.toggle("is-open");
            menuButton.setAttribute("aria-expanded", String(isOpen));
            menuButton.setAttribute("aria-label", isOpen ? "Close navigation" : "Open navigation");
        });

        navigation.addEventListener("click", (event) => {
            const link = event.target.closest(".workshop-nav-link");
            if (!link) return;

            navigation.querySelectorAll(".workshop-nav-link").forEach((navLink) => {
                navLink.classList.remove("is-active");
                navLink.removeAttribute("aria-current");
            });
            link.classList.add("is-active");
            link.setAttribute("aria-current", "page");
            navigation.classList.remove("is-open");
            menuButton.setAttribute("aria-expanded", "false");
            menuButton.setAttribute("aria-label", "Open navigation");
        });
    }

    const accountMenuTrigger = document.getElementById("account-menu-trigger");
    const accountMenu = document.getElementById("account-menu");
    const workshopAccountName = document.querySelector("[data-workshop-account-name]");
    let workshopSessionPromise;
    const loadWorkshopSession = () => {
        if (!workshopSessionPromise) {
            workshopSessionPromise = fetch("/api/v1/session", {
                credentials: "same-origin",
                cache: "no-store"
            })
            .then((response) => {
                if (!response.ok) {
                    throw new Error(`Could not load the signed-in account (HTTP ${response.status}).`);
                }
                return response.json();
            });
        }
        return workshopSessionPromise;
    };
    const renderWorkshopAccount = (profile) => {
        const roleName = profile.role === "ADMIN"
            ? "Admin"
            : profile.role === "STAFF" ? "Staff" : null;
        if (!roleName) throw new Error("The signed-in account has an unsupported role.");

        document.querySelectorAll("[data-workshop-account-name]").forEach((element) => {
            element.textContent = roleName;
        });
        document.querySelectorAll("[data-workshop-account-avatar]").forEach((avatar) => {
            avatar.textContent = roleName.charAt(0);
        });
        document.querySelectorAll("[data-workshop-account-title]").forEach((title) => {
            title.textContent = `${roleName} Account`;
        });
    };
    if (workshopAccountName) {
        loadWorkshopSession()
            .then(renderWorkshopAccount)
            .catch((error) => console.error(error));
    }

    const workshopProfileButton = document.getElementById("profile-settings");
    if (workshopProfileButton && !document.getElementById("profile-settings-dialog")) {
        const createElement = (tagName, className, text) => {
            const element = document.createElement(tagName);
            if (className) element.className = className;
            if (text) element.textContent = text;
            return element;
        };
        const dialog = createElement("dialog", "profile-settings-dialog");
        dialog.id = "workshop-profile-dialog";
        dialog.setAttribute("aria-labelledby", "workshop-profile-title");
        const form = document.createElement("form");
        form.noValidate = true;
        const heading = createElement("header", "profile-settings-page-heading");
        const headingText = document.createElement("div");
        headingText.append(
            createElement("h2", "", "Profile Settings"),
            createElement("p", "", "View your workshop account details.")
        );
        const closeButton = createElement("button", "profile-settings-close", "×");
        closeButton.type = "button";
        closeButton.setAttribute("aria-label", "Close profile settings");
        headingText.firstElementChild.id = "workshop-profile-title";
        heading.append(headingText, closeButton);

        const card = createElement("section", "profile-settings-card");
        const cardHeading = createElement("div", "profile-settings-card-heading");
        cardHeading.append(
            createElement("h3", "", "Account information"),
            createElement("p", "", "Your signed-in role and account email.")
        );
        const fields = createElement("div", "profile-information-fields");
        const createProfileField = (id, labelText, type = "text") => {
            const label = document.createElement("label");
            label.htmlFor = id;
            label.append(document.createTextNode(labelText));
            const input = document.createElement("input");
            input.id = id;
            input.type = type;
            label.append(input);
            fields.append(label);
            return input;
        };
        const nameInput = createProfileField("workshop-profile-name", "Display name");
        nameInput.maxLength = 100;
        nameInput.required = true;
        const emailInput = createProfileField("workshop-profile-email", "Email address", "email");
        emailInput.readOnly = true;
        const roleInput = createProfileField("workshop-profile-role", "Role");
        roleInput.readOnly = true;
        const note = createElement("p", "profile-settings-message");
        note.id = "workshop-profile-note";
        note.hidden = true;
        const actions = createElement("footer", "profile-settings-actions");
        const cancelButton = createElement("button", "profile-settings-cancel", "Close");
        cancelButton.type = "button";
        const saveButton = createElement("button", "sign-in-button", "Save display name");
        saveButton.type = "submit";
        actions.append(cancelButton, saveButton);
        form.append(heading, card, note, actions);
        card.append(cardHeading, fields);
        dialog.append(form);
        document.body.append(dialog);

        const closeDialog = () => dialog.close();
        closeButton.addEventListener("click", closeDialog);
        cancelButton.addEventListener("click", closeDialog);
        dialog.addEventListener("click", (event) => {
            if (event.target === dialog) closeDialog();
        });
        workshopProfileButton.addEventListener("click", async () => {
            note.hidden = true;
            saveButton.hidden = true;
            nameInput.readOnly = true;
            dialog.showModal();
            try {
                const profile = await loadWorkshopSession();
                renderWorkshopAccount(profile);
                nameInput.value = profile.displayName || "";
                emailInput.value = profile.email || "";
                roleInput.value = profile.role === "ADMIN" ? "Administrator" : "Staff";
                if (profile.role === "STAFF") {
                    nameInput.readOnly = false;
                    saveButton.hidden = false;
                    note.hidden = true;
                } else {
                    note.textContent = "Administrator credentials are managed in the server environment and cannot be changed here.";
                    note.hidden = false;
                }
            } catch (error) {
                note.textContent = error.message || "Unable to load your workshop account.";
                note.hidden = false;
            }
        });
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!nameInput.reportValidity()) return;
            saveButton.disabled = true;
            note.hidden = true;
            try {
                const response = await fetch("/api/v1/session/profile", {
                    method: "PATCH",
                    credentials: "same-origin",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ displayName: nameInput.value.trim() })
                });
                const result = await response.json().catch(() => ({}));
                if (!response.ok) {
                    throw new Error(result.error || result.message || `Unable to save profile (HTTP ${response.status}).`);
                }
                workshopSessionPromise = Promise.resolve(result);
                renderWorkshopAccount(result);
                const feedback = document.getElementById("profile-settings-feedback");
                if (feedback) {
                    feedback.textContent = "Your display name was updated.";
                    feedback.hidden = false;
                }
                closeDialog();
            } catch (error) {
                note.textContent = error.message || "Unable to save your profile.";
                note.hidden = false;
            } finally {
                saveButton.disabled = false;
            }
        });
    }
    const accountPhoto = document.querySelector("[data-customer-photo]");
    const accountPhotoPlaceholder = document.querySelector("[data-customer-photo-placeholder]");
    const updateAccountPhoto = (refresh = false) => {
        if (!accountPhoto || !accountPhotoPlaceholder) return;
        accountPhoto.hidden = true;
        accountPhotoPlaceholder.hidden = false;
        accountPhoto.onload = () => {
            accountPhoto.hidden = false;
            accountPhotoPlaceholder.hidden = true;
        };
        accountPhoto.onerror = () => {
            accountPhoto.hidden = true;
            accountPhotoPlaceholder.hidden = false;
        };
        accountPhoto.src = `/api/v1/customer/profile/photo${refresh ? `?v=${Date.now()}` : ""}`;
    };
    if (document.querySelector("[data-customer-name]")) {
        updateAccountPhoto();
        fetch("/api/v1/customer/profile")
            .then((response) => {
                if (!response.ok) {
                    throw new Error(`Could not load the customer profile (HTTP ${response.status}).`);
                }
                return response.json();
            })
            .then((profile) => {
                const fullName = typeof profile.fullName === "string" ? profile.fullName.trim() : "";
                if (!fullName) throw new Error("The customer profile did not include a name.");
                document.querySelectorAll("[data-customer-name]").forEach((element) => {
                    element.textContent = fullName;
                });
                document.querySelectorAll("[data-customer-account-name]").forEach((element) => {
                    element.textContent = fullName;
                });
                const emailInput = document.getElementById("profile-email");
                const addressInput = document.getElementById("profile-address");
                const phoneInput = document.getElementById("profile-phone");
                const nameInput = document.getElementById("profile-full-name");
                if (emailInput) emailInput.value = profile.email || "";
                if (addressInput) addressInput.value = profile.address || "";
                if (phoneInput) phoneInput.value = profile.phone || "";
                if (nameInput) nameInput.value = fullName;
            })
            .catch((error) => {
                console.error(error);
                const feedback = document.getElementById("profile-settings-feedback");
                if (feedback) {
                    feedback.hidden = false;
                    feedback.textContent = "Unable to load your account name. Please sign in again.";
                }
            });
    }

    if (accountMenuTrigger && accountMenu) {
        const setAccountMenuOpen = (isOpen) => {
            accountMenu.hidden = !isOpen;
            accountMenuTrigger.setAttribute("aria-expanded", String(isOpen));
        };

        accountMenuTrigger.addEventListener("click", () => {
            setAccountMenuOpen(accountMenu.hidden);
        });

        document.addEventListener("click", (event) => {
            if (!event.target.closest(".account-menu")) setAccountMenuOpen(false);
        });

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && !accountMenu.hidden) {
                setAccountMenuOpen(false);
                accountMenuTrigger.focus();
            }
        });

        const profileDialog = document.getElementById("profile-settings-dialog");
        const profileForm = document.getElementById("profile-settings-form");
        if (profileDialog && profileForm) {
            const message = document.getElementById("profile-settings-form-message");
            const photoInput = document.getElementById("profile-photo-input");
            const photoImage = document.getElementById("profile-photo-image");
            const photoPlaceholder = document.getElementById("profile-photo-placeholder");
            let photoObjectUrl;
            const setProfileMessage = (text, isError = true) => {
                message.textContent = text;
                message.hidden = !text;
                message.style.color = isError ? "#b42318" : "#356b45";
            };
            const setPhotoPreview = (source) => {
                if (photoObjectUrl) URL.revokeObjectURL(photoObjectUrl);
                photoObjectUrl = source.startsWith("blob:") ? source : undefined;
                photoImage.src = source;
                photoImage.hidden = false;
                photoPlaceholder.hidden = true;
                photoImage.onerror = () => {
                    photoImage.hidden = true;
                    photoPlaceholder.hidden = false;
                    if (photoObjectUrl) URL.revokeObjectURL(photoObjectUrl);
                    photoObjectUrl = undefined;
                };
            };
            const loadPhotoPreview = async () => {
                const response = await fetch("/api/v1/customer/profile/photo", { cache: "no-store" });
                if (response.status === 404) {
                    photoImage.hidden = true;
                    photoPlaceholder.hidden = false;
                    return;
                }
                if (!response.ok) throw new Error("Unable to load your profile photo.");
                setPhotoPreview(URL.createObjectURL(await response.blob()));
            };

            photoInput.addEventListener("change", () => {
                const [file] = photoInput.files;
                if (!file) return;
                if (!["image/jpeg", "image/png"].includes(file.type)) {
                    photoInput.value = "";
                    setProfileMessage("Choose a JPG or PNG image.");
                    return;
                }
                if (file.size > 2 * 1024 * 1024) {
                    photoInput.value = "";
                    setProfileMessage("Profile photos must be 2 MB or smaller.");
                    return;
                }
                setProfileMessage("");
                setPhotoPreview(URL.createObjectURL(file));
            });

            document.getElementById("profile-settings").addEventListener("click", async () => {
                setProfileMessage("");
                profileDialog.showModal();
                try {
                    const response = await fetch("/api/v1/customer/profile");
                    if (!response.ok) {
                        throw new Error(response.status === 401
                            ? "Your session has expired. Sign in again."
                            : "Unable to load your account details.");
                    }
                    const profile = await response.json();
                    profileForm.elements.fullName.value = profile.fullName || "";
                    profileForm.elements.email.value = profile.email || "";
                    profileForm.elements.address.value = profile.address || "";
                    profileForm.elements.phone.value = profile.phone || "";
                    profileForm.elements.currentPassword.value = "";
                    profileForm.elements.newPassword.value = "";
                    profileForm.elements.confirmPassword.value = "";
                    photoInput.value = "";
                    await loadPhotoPreview();
                } catch (error) {
                    console.error(error);
                    setProfileMessage(error.message);
                }
            });

            document.querySelectorAll("[data-close-profile-settings]").forEach((button) => {
                button.addEventListener("click", () => profileDialog.close());
            });
            profileDialog.addEventListener("click", (event) => {
                if (event.target === profileDialog) profileDialog.close();
            });
            profileDialog.addEventListener("close", () => {
                profileForm.reset();
                setProfileMessage("");
                if (photoObjectUrl) URL.revokeObjectURL(photoObjectUrl);
                photoObjectUrl = undefined;
                photoImage.removeAttribute("src");
                photoImage.hidden = true;
                photoPlaceholder.hidden = false;
            });
            profileForm.addEventListener("submit", async (event) => {
                event.preventDefault();
                const update = new FormData(profileForm);
                const newPassword = update.get("newPassword");
                const confirmPassword = update.get("confirmPassword");
                if (newPassword && newPassword.length < 12) {
                    setProfileMessage("A new password must be at least 12 characters.");
                    return;
                }
                if (newPassword !== confirmPassword) {
                    setProfileMessage("The new password fields do not match.");
                    return;
                }
                try {
                    const response = await fetch("/api/v1/customer/profile", {
                        method: "PUT",
                        body: update
                    });
                    const result = await response.json().catch(() => ({}));
                    if (!response.ok) {
                        throw new Error(result.error || "Unable to save your profile. Please try again.");
                    }
                    document.querySelectorAll("[data-customer-name]").forEach((element) => {
                        element.textContent = result.fullName;
                    });
                    document.querySelectorAll("[data-customer-account-name]").forEach((element) => {
                        element.textContent = result.fullName;
                    });
                    profileForm.elements.email.value = result.email;
                    profileForm.elements.phone.value = result.phone;
                    profileForm.elements.address.value = result.address;
                    updateAccountPhoto(true);
                    const feedback = document.getElementById("profile-settings-feedback");
                    if (feedback) {
                        feedback.hidden = false;
                        feedback.textContent = "Your profile was updated.";
                    }
                    profileDialog.close();
                } catch (error) {
                    console.error(error);
                    setProfileMessage(error.message);
                }
            });
        }
    }

    const reportDate = document.getElementById("report-date");
    if (reportDate) {
        const reportDateLabel = document.getElementById("report-date-label");
        const reportDateButton = document.getElementById("report-date-button");
        const reportsTableBody = document.getElementById("reports-table-body");
        const reportsEmptyState = document.getElementById("reports-empty-state");
        const reportsToggle = document.getElementById("toggle-reports");
        const reportRows = [...reportsTableBody.querySelectorAll("tr")];
        let showAllReports = false;
        let dateFilterActive = Boolean(reportDate.value);
        if (reportRows.length === 0) {
            reportsToggle.disabled = true;
            reportsToggle.textContent = "No reports available";
        }
        const formatReportDate = (value) => {
            const [year, month, day] = value.split("-").map(Number);
            return new Intl.DateTimeFormat(undefined, {
                day: "numeric",
                month: "long",
                year: "numeric",
                timeZone: "UTC"
            }).format(new Date(Date.UTC(year, month - 1, day)));
        };
        const renderReports = () => {
            let visibleCount = 0;
            reportRows.forEach((row) => {
                const matchesDate = !dateFilterActive || row.dataset.reportDate === reportDate.value;
                const isRecent = !row.classList.contains("report-older-row");
                const visible = matchesDate && (dateFilterActive || showAllReports || isRecent);
                row.hidden = !visible;
                if (visible) visibleCount += 1;
            });

            reportsEmptyState.hidden = visibleCount > 0;
            reportsEmptyState.textContent = dateFilterActive
                ? `No reports were generated on ${formatReportDate(reportDate.value)}.`
                : "No reports have been generated yet.";
            if (reportRows.length > 0) {
                reportsToggle.textContent = dateFilterActive
                    ? "Clear date filter"
                    : showAllReports ? "Show recent reports" : "View all reports";
                reportsToggle.setAttribute("aria-expanded", String(!dateFilterActive && showAllReports));
            }
        };

        reportDateButton.addEventListener("click", () => {
            if (typeof reportDate.showPicker === "function") {
                reportDate.showPicker();
            } else {
                reportDate.click();
            }
        });
        reportDate.addEventListener("change", () => {
            if (reportDate.value) {
                dateFilterActive = true;
                reportDateLabel.textContent = formatReportDate(reportDate.value);
            } else {
                dateFilterActive = false;
                reportDateLabel.textContent = "All dates";
            }
            showAllReports = false;
            renderReports();
        });
        if (dateFilterActive) reportDateLabel.textContent = formatReportDate(reportDate.value);
        renderReports();
    }

    const reportsToggle = document.getElementById("toggle-reports");
    if (reportsToggle) {
        const reportDateInput = document.getElementById("report-date");
        const olderReports = [...document.querySelectorAll(".report-older-row")];
        reportsToggle.addEventListener("click", () => {
            if (reportsToggle.disabled) return;
            if (reportDateInput && reportDateInput.value) {
                reportDateInput.value = "";
                reportDateInput.dispatchEvent(new Event("change"));
                return;
            }
            const showAll = reportsToggle.getAttribute("aria-expanded") !== "true";
            olderReports.forEach((row) => {
                row.hidden = !showAll;
            });
            reportsToggle.setAttribute("aria-expanded", String(showAll));
            reportsToggle.textContent = showAll ? "Show recent reports" : "View all reports";
        });
    }

    // Staff and inventory now use server-backed page modules.

    const wastageForm = document.getElementById("wastage-form");
    if (wastageForm) {
        const feedback = document.getElementById("wastage-feedback");
        const logsBody = document.getElementById("wastage-logs");
        const emptyState = document.getElementById("wastage-empty");
        const materialSelect = document.getElementById("material");
        const materialState = document.getElementById("wastage-material-state");
        const quantityInput = document.getElementById("quantity");
        const quantityFraction = document.getElementById("quantity-fraction");
        const unitSelect = document.getElementById("unit");
        const saveButton = document.getElementById("save-log");
        const labourCost = 45;
        const legacyMaterialUnitCosts = {
            "Mirror Glass": 75,
            "MDF Boards": 45,
            "Aluminum Frames": 65,
            Adhesives: 25,
            "Wall Mounts": 30,
            "Protective Films": 12,
            "Edge Trim": 18,
            "Cleaning Solution": 20
        };
        const currency = new Intl.NumberFormat(undefined, {
            style: "currency",
            currency: "PHP"
        });

        const showFeedback = (message, isError = false) => {
            feedback.textContent = message;
            feedback.classList.toggle("is-error", isError);
            feedback.classList.toggle("is-success", !isError && message.length > 0);
        };

        const showMaterialState = (message, isError = false) => {
            materialState.textContent = message;
            materialState.hidden = !message;
            materialState.classList.toggle("is-error", isError);
        };

        const selectedMaterial = () => materialSelect.selectedOptions[0];

        const updateImpact = () => {
            const selected = selectedMaterial();
            const unitCost = Number(selected && selected.dataset.unitCost);
            const quantity = Number(quantityInput.value) + Number(quantityFraction.value);
            const materialAmount = Number.isFinite(quantity) && quantity > 0 && Number.isFinite(unitCost)
                ? quantity * unitCost
                : 0;
            document.getElementById("material-cost").textContent = currency.format(materialAmount);
            document.getElementById("labour-cost").textContent = currency.format(labourCost);
            document.getElementById("total-impact").textContent = currency.format(materialAmount + labourCost);
        };

        const updateSelectedMaterial = () => {
            const option = selectedMaterial();
            const hasMaterial = Boolean(option && option.value);
            const unitCode = hasMaterial ? option.dataset.unitCode : "";
            const unitName = hasMaterial ? option.dataset.unitName : "";
            unitSelect.replaceChildren();
            const unitOption = document.createElement("option");
            unitOption.value = unitCode;
            unitOption.textContent = hasMaterial ? `${unitName} (${unitCode})` : "Select a material first";
            unitSelect.append(unitOption);
            unitSelect.disabled = !hasMaterial;

            const isCountedUnit = unitCode === "EA";
            quantityFraction.value = "0";
            quantityFraction.disabled = !hasMaterial || isCountedUnit;
            saveButton.disabled = !hasMaterial;
            updateImpact();
        };

        const loadMaterials = async () => {
            materialSelect.disabled = true;
            saveButton.disabled = true;
            materialSelect.replaceChildren();
            const loadingOption = document.createElement("option");
            loadingOption.value = "";
            loadingOption.textContent = "Loading active inventory materials…";
            materialSelect.append(loadingOption);
            showMaterialState("Loading active inventory materials…");
            try {
                if (!window.StyleMariaApi) {
                    throw new Error("The inventory API client did not load. Refresh the page and try again.");
                }
                const materials = await window.StyleMariaApi.request("/api/v1/inventory/materials");
                if (!Array.isArray(materials)) {
                    throw new Error("The inventory catalog returned an invalid response.");
                }
                materialSelect.replaceChildren();
                const placeholder = document.createElement("option");
                placeholder.value = "";
                placeholder.textContent = materials.length
                    ? "Select an active inventory material"
                    : "No active inventory materials";
                placeholder.selected = true;
                materialSelect.append(placeholder);

                materials.forEach((material) => {
                    const option = document.createElement("option");
                    option.value = String(material.id);
                    option.textContent = `${material.name} — ${material.unitName} (${material.unitCode})`;
                    option.dataset.materialName = material.name;
                    option.dataset.unitCode = material.unitCode;
                    option.dataset.unitName = material.unitName;
                    option.dataset.unitCost = String(legacyMaterialUnitCosts[material.name] || 0);
                    materialSelect.append(option);
                });

                materialSelect.disabled = materials.length === 0;
                showMaterialState(materials.length
                    ? ""
                    : "No active inventory materials are available. Ask an administrator to add one in Inventory.");
                updateSelectedMaterial();
            } catch (error) {
                materialSelect.replaceChildren();
                const errorOption = document.createElement("option");
                errorOption.value = "";
                errorOption.textContent = "Unable to load inventory materials";
                errorOption.selected = true;
                materialSelect.append(errorOption);
                materialSelect.disabled = true;
                unitSelect.replaceChildren();
                const unitOption = document.createElement("option");
                unitOption.value = "";
                unitOption.textContent = "Select a material first";
                unitSelect.append(unitOption);
                unitSelect.disabled = true;
                quantityFraction.disabled = true;
                saveButton.disabled = true;
                showMaterialState(
                    `${error.message || "Unable to load active inventory materials."} Sign in with a staff account and retry.`,
                    true
                );
                updateImpact();
            }
        };

        const appendCell = (row, value) => {
            const cell = document.createElement("td");
            cell.textContent = value;
            row.append(cell);
        };

        const renderLogs = (logs) => {
            logsBody.replaceChildren();
            logs.forEach((log) => {
                const row = document.createElement("tr");
                appendCell(row, log.orderId);
                appendCell(row, log.material);
                appendCell(row, `${log.quantity} ${log.unit}`);
                appendCell(row, log.reason);

                const statusCell = document.createElement("td");
                const status = document.createElement("span");
                status.className = "status";
                status.textContent = log.status;
                statusCell.append(status);
                row.append(statusCell);

                const impactCell = document.createElement("td");
                impactCell.textContent = currency.format(Number(log.totalImpact));
                impactCell.title = `Logged ${new Intl.DateTimeFormat(undefined, {
                    dateStyle: "medium",
                    timeStyle: "short"
                }).format(new Date(log.loggedAt))}`;
                row.append(impactCell);
                logsBody.append(row);
            });
            emptyState.hidden = logs.length > 0;
        };

        const loadLogs = async () => {
            const response = await fetch("/api/v1/wastage");
            if (!response.ok) {
                throw new Error(`Unable to load saved logs (${response.status}).`);
            }
            renderLogs(await response.json());
        };

        const params = new URLSearchParams(window.location.search);
        const orderId = params.get("orderId");
        if (orderId) {
            document.getElementById("order-id").value = orderId;
        }
        const now = new Date();
        document.getElementById("today-date").textContent = new Intl.DateTimeFormat(undefined, {
            day: "numeric",
            month: "short",
            year: "numeric"
        }).format(now);
        document.getElementById("logged-on").value = new Intl.DateTimeFormat(undefined, {
            day: "numeric",
            month: "short",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit"
        }).format(now);
        const localDate = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
        document.getElementById("delivery-date").value = localDate.toISOString().slice(0, 10);

        materialSelect.addEventListener("change", updateSelectedMaterial);
        quantityInput.addEventListener("input", updateImpact);
        quantityFraction.addEventListener("change", updateImpact);
        updateImpact();

        loadMaterials();
        loadLogs().catch((error) => {
            showFeedback(error.message, true);
        });

        wastageForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            const materialOption = selectedMaterial();
            if (!materialOption || !materialOption.value || saveButton.disabled) {
                showFeedback("Select an active inventory material before saving.", true);
                return;
            }
            const formData = new FormData(wastageForm);
            const payload = {
                orderId: formData.get("orderId"),
                material: materialOption.dataset.materialName,
                materialId: Number(formData.get("materialId")),
                description: formData.get("description"),
                reason: formData.get("reason"),
                actionTaken: formData.get("actionTaken"),
                quantity: Number(formData.get("quantity")) + Number(quantityFraction.value),
                unit: materialOption.dataset.unitCode,
                loggedBy: formData.get("loggedBy"),
                orderStage: formData.get("orderStage"),
                deliveryDate: formData.get("deliveryDate"),
                recoverable: document.getElementById("recoverable").value === "true",
                status: document.getElementById("log-status").value
            };

            saveButton.disabled = true;
            showFeedback("Saving wastage log...");
            try {
                const response = await fetch("/api/v1/wastage", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                });
                const result = await response.json();
                if (!response.ok) {
                    throw new Error(result.message || `Unable to save wastage log (${response.status}).`);
                }

                showFeedback(`Wastage log saved for ${result.orderId}.`);
                document.getElementById("order-id").value = "";
                document.getElementById("description").value = "";
                await loadLogs();
            } catch (error) {
                showFeedback(error.message || "Unable to save the wastage log.", true);
            } finally {
                saveButton.disabled = !materialSelect.value;
            }
        });
    }

    const mirrorBuilderForm = document.getElementById("mirror-builder-form");
    if (mirrorBuilderForm) {
        const heightInput = document.getElementById("mirror-height");
        const widthInput = document.getElementById("mirror-width");
        const thicknessSelect = document.getElementById("mirror-thickness");
        const frameMaterialSelect = document.getElementById("frame-material");
        const frameWidthSelect = document.getElementById("frame-width");
        const edgeFinishInput = document.getElementById("edge-finish");
        const shapeInput = document.getElementById("mirror-shape");
        const laborInput = document.getElementById("labor-cost-input");
        const money = new Intl.NumberFormat("en-PH", {
            style: "currency",
            currency: "PHP",
            minimumFractionDigits: 2
        });
        const roundMoney = (amount) => Math.round((amount + Number.EPSILON) * 100) / 100;
        const edgeRates = { polished: 1.5, beveled: 2.5, pencil: 1.8 };
        let materialTotal = 2770;

        const calculatePerimeter = (height, width, shape) => {
            if (shape === "round") return Math.PI * Math.min(height, width);
            if (shape === "oval") {
                const a = height / 2;
                const b = width / 2;
                return Math.PI * (3 * (a + b) - Math.sqrt((3 * a + b) * (a + 3 * b)));
            }
            if (shape === "arch") return (2 * height) + ((Math.PI / 2 - 1) * width);
            return 2 * (height + width);
        };

        const updateBuilder = () => {
            const height = Number(heightInput.value);
            const width = Number(widthInput.value);
            if (!Number.isFinite(height) || !Number.isFinite(width) || height <= 0 || width <= 0) return;

            const shape = shapeInput.value;
            const areaSquareInches = shape === "round"
                ? Math.PI * (Math.min(height, width) / 2) ** 2
                : shape === "oval"
                    ? Math.PI * height * width / 4
                    : shape === "arch"
                        ? height * width * 0.9
                        : height * width;
            const areaSquareFeet = areaSquareInches / 144;
            const perimeterInches = calculatePerimeter(height, width, shape);
            const frameFeet = Math.ceil(perimeterInches / 12);
            const sheetCount = Math.max(1, Math.ceil(areaSquareFeet / 32));
            const thickness = Number(thicknessSelect.value);
            const glassCost = roundMoney(areaSquareFeet * 125.625 * (thickness / 0.25));
            const frameRate = Number(frameMaterialSelect.selectedOptions[0].dataset.rate);
            const frameWidthFactor = Number(frameWidthSelect.value) / 2;
            const frameCost = roundMoney(frameFeet * frameRate * frameWidthFactor);
            const backingCost = sheetCount * 250;
            const edgeName = edgeFinishInput.value === "beveled"
                ? "Beveled Edge"
                : edgeFinishInput.value === "pencil" ? "Pencil Edge" : "Edge Polishing";
            const edgeCost = roundMoney(perimeterInches * edgeRates[edgeFinishInput.value]);

            materialTotal = roundMoney(glassCost + frameCost + backingCost + 120 + 100 + edgeCost);
            const laborCost = Math.max(0, Number(laborInput.value) || 0);
            const overhead = roundMoney((materialTotal + laborCost) * 0.15);
            const baseline = roundMoney(materialTotal + laborCost + overhead);
            const glassThickness = thickness === 0.1875 ? "3/16" : thickness === 0.375 ? "3/8" : "1/4";
            const frameName = frameMaterialSelect.selectedOptions[0].textContent.trim();

            document.getElementById("glass-material-name").textContent = `Mirror Glass (${glassThickness}\")`;
            document.getElementById("glass-quantity").textContent = areaSquareFeet.toFixed(2);
            document.getElementById("glass-cost").textContent = money.format(glassCost);
            document.getElementById("frame-material-name").textContent = `${frameName} Frame`;
            document.getElementById("frame-quantity").textContent = frameFeet.toFixed(2);
            document.getElementById("frame-cost").textContent = money.format(frameCost);
            document.getElementById("backing-quantity").textContent = sheetCount.toFixed(2);
            document.getElementById("backing-cost").textContent = money.format(backingCost);
            document.getElementById("edge-material-name").textContent = edgeName;
            document.getElementById("edge-quantity").textContent = perimeterInches.toFixed(2);
            document.getElementById("edge-cost").textContent = money.format(edgeCost);
            document.getElementById("material-total").textContent = money.format(materialTotal);
            document.getElementById("baseline-material-cost").textContent = money.format(materialTotal);
            document.getElementById("labor-cost-display").textContent = money.format(laborCost);
            document.getElementById("overhead-cost").textContent = money.format(overhead);
            document.getElementById("baseline-price").textContent = money.format(baseline);
        };

        [heightInput, widthInput, thicknessSelect, frameMaterialSelect, frameWidthSelect, laborInput]
            .forEach((control) => {
                control.addEventListener("input", updateBuilder);
                control.addEventListener("change", updateBuilder);
            });

        mirrorBuilderForm.querySelectorAll("[data-choice-group]").forEach((button) => {
            button.addEventListener("click", () => {
                const group = button.dataset.choiceGroup;
                mirrorBuilderForm.querySelectorAll(`[data-choice-group="${group}"]`).forEach((option) => {
                    const isSelected = option === button;
                    option.classList.toggle("is-selected", isSelected);
                    option.setAttribute("aria-pressed", String(isSelected));
                });
                if (group === "edge") edgeFinishInput.value = button.dataset.choiceValue;
                if (group === "shape") shapeInput.value = button.dataset.choiceValue;
                updateBuilder();
            });
        });

        mirrorBuilderForm.querySelectorAll("[data-builder-step]").forEach((step) => {
            step.addEventListener("click", () => {
                mirrorBuilderForm.querySelectorAll("[data-builder-step]").forEach((otherStep) => {
                    const isActive = otherStep === step;
                    otherStep.classList.toggle("is-active", isActive);
                    otherStep.classList.toggle("is-complete", otherStep.compareDocumentPosition(step) & Node.DOCUMENT_POSITION_FOLLOWING);
                    if (isActive) otherStep.setAttribute("aria-current", "step");
                    else otherStep.removeAttribute("aria-current");
                });
            });
        });

        const laborEditButton = document.getElementById("edit-labor-cost");
        laborEditButton.addEventListener("click", () => {
            const editing = laborInput.hidden;
            laborInput.hidden = !editing;
            laborEditButton.setAttribute("aria-label", editing ? "Finish editing labor estimate" : "Edit labor estimate");
            document.getElementById("labor-cost-display").hidden = editing;
            if (editing) laborInput.focus();
        });

        let resetFeedbackTimeout;
        mirrorBuilderForm.addEventListener("reset", (event) => {
            if (event.target !== mirrorBuilderForm) return;
            window.clearTimeout(resetFeedbackTimeout);
            window.setTimeout(() => {
                mirrorBuilderForm.querySelectorAll("[data-choice-group]").forEach((button) => {
                    const isSelected = (button.dataset.choiceGroup === "edge" && button.dataset.choiceValue === "polished")
                        || (button.dataset.choiceGroup === "shape" && button.dataset.choiceValue === "rectangle");
                    button.classList.toggle("is-selected", isSelected);
                    button.setAttribute("aria-pressed", String(isSelected));
                });
                mirrorBuilderForm.querySelectorAll("[data-builder-step]").forEach((step, index) => {
                    step.classList.toggle("is-active", index === 0);
                    step.classList.remove("is-complete");
                    if (index === 0) step.setAttribute("aria-current", "step");
                    else step.removeAttribute("aria-current");
                });
                laborInput.hidden = true;
                document.getElementById("labor-cost-display").hidden = false;
                laborEditButton.setAttribute("aria-label", "Edit labor estimate");
                updateBuilder();
                resetFeedbackTimeout = window.setTimeout(() => {
                    showToast("Calculator reset to its default settings.");
                }, 0);
            }, 0);
        });

        document.getElementById("generate-quote").addEventListener("click", () => {
            if (!mirrorBuilderForm.reportValidity()) return;
            const height = Number(heightInput.value);
            const width = Number(widthInput.value);
            const finalPrice = document.getElementById("baseline-price").textContent;
            showToast(`Quote preview generated for ${height} × ${width} in. Baseline price: ${finalPrice}.`);
        });

        mirrorBuilderForm.addEventListener("submit", (event) => {
            event.preventDefault();
        });

        updateBuilder();
    }

    // POS orders now use the server-backed API module.

    // Business order pages use server-backed APIs; no browser copy is loaded here.
})();