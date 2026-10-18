# Style Maria Workshop

Customer ordering + payment platform for a custom mirror / frame workshop.
Built with **Spring Boot (Java 17) + MySQL** backend and **plain HTML / CSS / JavaScript** frontend.

```
style-maria/
├── backend/        Spring Boot + MySQL (REST API, port 8080)
├── frontend/       Static HTML/CSS/JS pages (open directly or serve via any static server)
└── database/       Reference schema.sql
```

## 1. Prerequisites
- **Java 17+**  (`java -version`)
- **Maven 3.8+**  (`mvn -v`)
- **MySQL 8+** running locally (default: `localhost:3306`, user `root`, pass `root`)
- VS Code (recommended extensions: Extension Pack for Java, Live Server)

## 2. Database setup
Spring Boot auto-creates the schema on first run (`spring.jpa.hibernate.ddl-auto=update`), so you only need to make sure MySQL is running and the credentials in `backend/src/main/resources/application.properties` match your setup. Change these three lines if needed:

```
spring.datasource.url=jdbc:mysql://localhost:3306/style_maria?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=root
```

A reference `database/schema.sql` is included if you want to create tables manually.

## 3. Run the backend
Open a terminal in `backend/`:

```bash
cd backend
mvn spring-boot:run
```

The API starts on **http://localhost:8080**.
On first start, an admin user is seeded:
- **Email:** `admin@stylemaria.com`
- **Password:** `admin123`

## 4. Run the frontend
The frontend is pure HTML/CSS/JS. The simplest way:

**Option A — VS Code Live Server:**
Right-click `frontend/index.html` → **Open with Live Server**.

**Option B — any static server:**
```bash
cd frontend
python3 -m http.server 5500
# then open http://localhost:5500
```

The frontend talks to the backend at `http://localhost:8080` by default. To change this, add a tiny script before `<script src="js/common.js"></script>` on each page, e.g.:
```html
<script>window.SM_API_BASE = 'http://your-server:8080';</script>
```

## 5. Flow (matches your mockups)
1. **Sign in / Create account** — `index.html`
2. **Order** (dimensions + material + optional reference image) — `order.html`
3. **Cart** — adjust quantity / remove items — `cart.html`
4. **Checkout** — scan QR, upload proof of payment — `checkout.html`
5. **Track order** — look up by reference number — `track.html`
6. **Admin dashboard** — view all orders, change statuses, update QR image — `admin.html`

## Open in VS Code
Unzip, then **File → Open Folder… → `style-maria`**. VS Code will suggest the recommended extensions (Java Pack, Spring Boot, Live Server).
- Backend: press **F5** ("Run Style Maria Backend") or run `mvn spring-boot:run` in `backend/`.
- Frontend: click **Go Live** in the status bar (serves `frontend/` on port 5500).

## Delivery address & fees
Customers enter recipient, mobile, street, city, province and pick a delivery area on the Cart page. Fees (edit in `DeliveryFees.java`):
Metro Manila ₱250 · Luzon ₱450 · Visayas ₱650 · Mindanao ₱750.

## Order confirmation email
After a customer submits payment, they get an email with the order reference, subtotal, delivery fee, total and address.
Configured in `application.properties`:
```
app.email.key=...           # leave empty to turn emails off
app.email.from-name=Style Maria Workshop
app.email.reply-to=         # optional: your inbox for customer replies
```

## Admin search & filters
Search orders by reference, customer name or email. Filter by status, including **"Receipts awaiting check"** (paid orders still in Pending).

## 6. Pricing (editable in `Pricing.java`)
Unit price = base + rate × (length × width)
- Solid Wood: base ₱500, rate ₱0.80 / cm²
- MDF:        base ₱300, rate ₱0.50 / cm²
- Aluminum:   base ₱400, rate ₱0.70 / cm²
- Delivery fee: depends on delivery area (see above)

## 7. Status values (order lifecycle)
`PENDING_PAYMENT` → `PENDING` → `IN_PRODUCTION` → `SCHEDULED` → `COMPLETED` (or `REJECTED`)

## 8. Uploaded files
All uploaded images (reference images, receipts, QR) are stored in `backend/uploads/` and served from `http://localhost:8080/uploads/...`.

## 9. API summary
Auth: `POST /api/auth/signup` · `POST /api/auth/login` · `GET /api/auth/me` · `POST /api/auth/logout`
Files: `POST /api/files/upload` (multipart `file`)
Quote: `GET /api/quote?length=&width=&material=`
Cart: `GET /api/cart` · `POST /api/cart` · `PUT /api/cart/{id}` · `DELETE /api/cart/{id}`
Orders: `POST /api/orders/checkout` (body: recipientName, phone, street, city, province, deliveryArea) · `POST /api/orders/{ref}/payment` · `GET /api/orders/mine` · `GET /api/orders/track/{ref}`
Public: `GET /api/qr`
Delivery: `GET /api/delivery-areas`
Admin: `GET /api/admin/orders?q=&status=` · `PUT /api/admin/orders/{ref}/status` · `GET/PUT /api/admin/settings/qr`
All authenticated endpoints require header: `Authorization: Bearer <token>` (token returned on login/signup).
