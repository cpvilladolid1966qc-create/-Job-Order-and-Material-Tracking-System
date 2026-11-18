# Account operations

## Roles and email addresses

Access is determined by the account role, not by an email suffix. Customers may register with any valid email address they can access. The application does not create or control Gmail addresses or domains.

- The single Admin identity is configured by the server operator with `ADMIN_EMAIL` and `ADMIN_PASSWORD`.
- Staff accounts are stored in the database with the `STAFF` role and are created only through an Admin invitation.
- Public registration creates customer (`USER`) accounts only; it cannot assign Admin or Staff access.

Configure outbound email (`SMTP_HOST`, `SMTP_PORT`, and, where required, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, and `SMTP_STARTTLS`) before enabling public registration, staff invitations, or password recovery. Keep credentials in the deployment environment or secret store, never in source control.

## Production deployment

The Docker image starts with the `prod` Spring profile. It intentionally requires deployment configuration rather than using the local development defaults. Set these values in the hosting provider's secret/environment settings:

- `APP_PUBLIC_URL`: the public HTTPS URL used in verification, recovery, and invitation emails.
- `ADMIN_EMAIL` and `ADMIN_PASSWORD`: the single Admin identity. Use a unique password with at least 16 characters.
- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`: a persistent MySQL database and dedicated database account. Connections use TLS certificate and hostname verification.
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `MAIL_FROM`: a verified outbound email account. Production SMTP authentication and STARTTLS are required.

The app should fail to start if required values are missing or if `APP_PUBLIC_URL` is not HTTPS. Never put these values in the Docker image, source files, screenshots, or chat messages. The Docker image runs as a non-root user; the hosting platform must route HTTPS traffic to the container's configured port and strip client-supplied forwarded headers before setting its own trusted forwarding headers.

The current schema is initialized by the application at startup. Before launch, use a persistent database, take a backup, and test restoring it. Plan a versioned database migration process before making production schema changes.

## Customer registration

Customers register with their own email address and must open the single-use verification link sent to that address before signing in. The link expires after 24 hours. If it expires or is lost, use **Resend verification email** on the sign-in page. Password reset links expire after 30 minutes.

## Staff account lifecycle

1. An Admin opens **Manage Staff** in the Admin dashboard and invites the staff member's real email address.
2. The staff member follows the one-time invitation link within 24 hours and sets a password of at least 12 characters.
3. To disable access, the Admin deactivates the account in **Manage Staff**. To restore access, an Admin explicitly activates it again.
4. An active staff member can request a password reset from **Forgot password?**. A disabled account cannot sign in or reset its password; an Admin must first confirm the person should regain access and reactivate the account.

## Admin account recovery

Admin credentials are deployment configuration, not database records, and cannot be looked up or changed in the website. If the current password is unknown, an authorized deployment operator must replace `ADMIN_PASSWORD` (and, if needed, `ADMIN_EMAIL`) in the hosting provider's secret store/environment, then restart the application. Use a unique password of at least 16 characters. Restarting clears the in-memory sessions. If deployment configuration is inaccessible, recover access through the hosting provider's authorized account-recovery process; do not create an Admin by changing a customer's or staff member's email.
