# Enterprise Ticket Management System

A full-stack IT helpdesk ticketing platform built with **Spring Boot 3** and **vanilla JavaScript**, featuring stateless JWT authentication with refresh tokens, role-based access control, an automated routing engine, and an SLA breach scheduler.

---

## Features

- **Stateless JWT authentication :** 15-minute access tokens, 24-hour refresh tokens, with automatic silent refresh on the frontend
- Role-based access control :  `DEVELOPER` and `ADMIN` roles enforced at the Spring Security filter chain level, not in business logic
- **Ticket lifecycle management :**  create, view, filter, acknowledge, and resolve tickets, with full description history preserved through every status change
- **Category management :**  admin-managed categories backed by a real database table and REST endpoint, not hardcoded frontend strings
- **Automated routing engine :**  tickets are auto-assigned to a target queue based on category/priority rules, falling back to a default triage queue
- **SLA breach scheduler :**  a background `@Scheduled` job sweeps for tickets that have missed their SLA deadline and flags them
- **JPA auditing :**  `@CreatedDate`/`@LastModifiedDate` timestamps managed automatically, no manual bookkeeping
- **Stored-XSS-safe rendering :**  all user-supplied ticket data is HTML-escaped before being rendered on the dashboard
- **Admin user provisioning :**  admins can create, list, and remove `DEVELOPER`/`ADMIN` accounts through a dedicated Access Management panel
- **Custom exception handling :**  a global `@ControllerAdvice` returns consistent, structured JSON error responses instead of raw Spring stack traces
- **Continuous Integration :** GitHub Actions runs the full test suite against an in-memory H2 database on every push

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3.4 |
| Security | Spring Security, JWT (jjwt 0.12.6), BCrypt password hashing |
| Persistence | Spring Data JPA, Hibernate, MySQL 8 (production), H2 in-memory (tests only) |
| Validation | Jakarta Bean Validation (JSR-303) |
| Frontend | HTML5, CSS3, Vanilla JavaScript (no frontend framework) |
| Build | Maven |
| CI | GitHub Actions |

---



## Getting Started

### Prerequisites
- Java 21+
- Maven 3.8+ (or use the included `./mvnw` wrapper)
- MySQL 8+, running locally

### 1. Clone the repository

```bash
git clone https://github.com/saicharanreddy01/enterprise-ticket-management-system.git
cd enterprise-ticket-management-system/ticketmaster
```

### 2. Create the database

```sql
CREATE DATABASE ticketmaster_db;
```

### 3. Set required environment variables

This application reads its database password and JWT signing secret from environment variables — **nothing sensitive is stored in the repository.** Set these before running:

| Variable | Description |
|---|---|
| `DB_PASSWORD` | Your local MySQL root (or dedicated app user) password |
| `JWT_SECRET` | A random 256-bit secret, e.g. generate one with `openssl rand -hex 32` |

In IntelliJ: **Run → Edit Configurations → Environment Variables**, and add both as `KEY=VALUE` pairs.

### 4. Run the application

```bash
./mvnw spring-boot:run
```

The app starts at `http://localhost:8080`, which redirects to the landing page.

### 5. Create your first account

There is currently no seed data — the database starts empty, and new account registration itself requires an existing `ADMIN` account (`POST /api/auth/register` is `ADMIN`-only). To bootstrap your first admin, insert one directly into MySQL with a BCrypt-hashed password:

```sql
-- Generate a BCrypt hash for your chosen password first (e.g. via https://bcrypt-generator.com,
-- or by temporarily calling PasswordEncoder.encode() in a scratch test), then:
INSERT INTO users (username, password, role)
VALUES ('admin', '<your-bcrypt-hash-here>', 'ADMIN');
```

Log in with that account, then use the **Access Management** panel in the dashboard to provision additional `DEVELOPER`/`ADMIN` accounts going forward — no further manual SQL needed.

---

## API Endpoints

All endpoints except `/api/auth/login` and `/api/auth/refresh` require a valid JWT in the `Authorization: Bearer <token>` header.

| Method | Endpoint | Role Required | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Authenticate, receive access + refresh tokens |
| `POST` | `/api/auth/refresh` | Public (valid refresh token) | Exchange a refresh token for a new access token |
| `POST` | `/api/auth/register` | `ADMIN` | Provision a new user account |
| `GET` | `/api/auth/users` | `ADMIN` | List all user accounts |
| `DELETE` | `/api/auth/users/{id}` | `ADMIN` | Remove a user account |
| `GET` | `/api/tickets` | Authenticated | Fetch all tickets |
| `POST` | `/api/tickets` | Authenticated | Create a new ticket |
| `GET` | `/api/tickets/status/{status}` | Authenticated | Filter tickets by status |
| `PUT` | `/api/tickets/{id}` | Authenticated | Update a ticket |
| `DELETE` | `/api/tickets/{id}` | `ADMIN` | Delete a ticket |
| `GET` | `/api/categories` | Authenticated | List all categories |
| `POST` | `/api/categories` | `ADMIN` | Create a new category |
| `GET` | `/api/users/me` | Authenticated | Get current authenticated user's info |

### Example: Create a ticket

```http
POST /api/tickets
Authorization: Bearer <your-access-token>
Content-Type: application/json

{
  "title": "Server RAM Upgrade Required",
  "description": "Production server needs upgrade to 32GB to prevent throttling.",
  "priority": "HIGH",
  "category": { "id": 1 }
}
```

### Example response

```json
{
  "id": 1,
  "title": "Server RAM Upgrade Required",
  "description": "Production server needs upgrade to 32GB to prevent throttling.",
  "status": "OPEN",
  "priority": "HIGH",
  "category": { "id": 1, "name": "Hardware" },
  "raisedBy": "dev_user",
  "assignedTo": "DEFAULT_TRIAGE_QUEUE",
  "resolvedBy": null,
  "slaDueDate": "2026-06-19T18:36:00",
  "slaBreached": false,
  "createdAt": "2026-06-19T14:36:00",
  "updatedAt": "2026-06-19T14:36:00"
}
```


---

## Key Design Decisions

- **3-layer architecture :**  Controller, Service, and Repository layers are kept strictly separate, following standard Spring conventions
- **Stateless JWT over sessions :**  no server-side session state to manage or scale; short-lived access tokens limit the blast radius of a leaked token
- **RBAC enforced at the security filter level :** role checks live in `SecurityConfig`, not scattered through business logic, so the permission model is auditable in one place
- **Category resolved server-side from ID :**  the frontend sends a category `id`, and `TicketService` resolves it against the database before saving, preventing Hibernate from trying to silently create orphaned category rows
- **Status semantics :**  `NEW` means unassigned/just raised; a ticket transitions to `OPEN` the moment the routing engine assigns it to a queue
- **Secrets via environment variables only :**  `application.properties` contains no real credentials; both the DB password and JWT secret are injected at runtime

---

## Known Limitations / Roadmap

- **No database seeding :** first admin account must be bootstrapped manually (see Getting Started, step 5)
- No N+1 query optimization yet on the ticket list endpoint (tracked for an upcoming commit)
- Logout currently clears the client-side token only; server-side refresh token revocation is planned

---

## Author

**P. Sai Charan Reddy** | B.Tech CSE(AI) MITS (2027) |
Stanford University Innovation Fellow |
[GitHub](https://github.com/saicharanreddy01)