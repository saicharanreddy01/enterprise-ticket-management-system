# Ticketmaster : Enterprise Ticket Management System

A full-stack, enterprise-grade IT service management (ITSM) / help desk platform built from scratch in Java and Spring Boot, with a vanilla JS frontend, no frameworks, no build step. Designed to go feature-for-feature against tools like Zendesk, Freshdesk, and Jira Service Management, while staying free to run and simple to reason about.

> **New to this codebase?** Read [`ARCHITECTURE.md`](./ARCHITECTURE.md) first, it's a 5-minute map of how the project is organized, which file owns which feature, and the conventions to follow before your first PR.

---

## Tech Stack

- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security, Spring Data JPA
- **Auth:** JWT (access + refresh tokens) via HttpOnly cookies, account lockout, password policy, optional TOTP-based 2FA
- **Database:** MySQL 8 (H2 in-memory for CI)
- **Frontend:** Vanilla JavaScript, HTML, CSS, no React/Vue/build tooling, split into feature-scoped files (see `static/js/`)
- **Rate limiting:** Bucket4j
- **CI:** GitHub Actions
- **LLM (optional):** Groq API (free tier), OpenAI-compatible endpoint, used for AI-assisted triage

---

## Project Structure

```
src/main/java/com/enterprise/ticketmaster/
├── model/         JPA entities + DTOs (one class per file)
├── repository/    Spring Data JPA interfaces
├── service/       Business logic, start here when tracing behavior
├── controller/    Thin REST layer
├── event/         Notification event bus
├── config/        Security, JWT, rate limiting
└── exception/     Global exception handling

src/main/resources/static/
├── index.html         Main dashboard shell
├── login.html         Sign-in page (separate from index.html/app scripts)
└── js/
    ├── core.js                        Shared auth helpers, global state, utilities, loads FIRST
    ├── notifications.js                Notification bell
    ├── dashboard-reports.js            Dashboard charts + Reports page
    ├── tickets-console.js              Main ticket table: search/filter/sort/bulk actions
    ├── ticket-create.js                "Raise Incident" form + AI suggestions + custom fields
    ├── ticket-detail.js                Ticket detail modal (comments, SLA, AI triage, merge)
    ├── settings-admin-security.js      Admin settings: users, security log, MFA, webhooks
    ├── settings-sla-routing-kb.js      Admin settings: SLA, routing, canned responses, KB
    └── shortcuts-init.js               Keyboard shortcuts + page bootstrap, loads LAST
```

Every package has its own `package-info.java`, and every JS file has a header comment explaining its scope and dependencies, full detail in `ARCHITECTURE.md`.

---

## Feature Overview

### Core Ticketing
- Full ticket lifecycle: create, assign, comment (public/internal), resolve, reopen, auto-close after 3 days
- Role-based access: **ADMIN**, **DEVELOPER** (agent), **USER** (restricted requester/customer portal view)
- Pagination, search/filtering, bulk operations (priority/category changes)
- File attachments, ticket linking, audit history log (per-field change tracking)
- Dark mode, keyboard shortcuts, loading skeletons

### SLA Management
- **Business-hours-aware SLA calendar:** admin-configurable work days/hours; SLA clocks pause outside business hours instead of running 24/7
- SLA pause-on-pending (clock stops while waiting on the customer)
- **Escalation policy on breach:** auto-bumps ticket priority one level and notifies a configurable per-category escalation contact (falls back to admin broadcast if none set)
- Configurable per-priority SLA thresholds, all admin-editable without redeploy

### Agent Productivity
- **Canned responses / macros:** admin-managed reusable reply templates, insertable directly into any comment box
- **Collision detection:** live "Agent X is also viewing this ticket" indicator (in-memory presence tracking, ~8s heartbeat)
- **Skill/load-based auto-assignment:** routing rules can auto-pick the least-loaded eligible agent from a configured pool, reusing the existing workload calculation

### Self-Service & Deflection
- **Knowledge base module:** admin-authorable articles; live "Related articles" suggestions surface during ticket creation (keyword-overlap search) to deflect tickets before they're filed; one-click "Convert resolved ticket into KB article"
- **CSAT survey:** one-click 1–5 star satisfaction rating emailed after resolution, with an admin summary card in Reports

### AI-Assisted Triage (Groq-powered, free tier)
- Auto-summarize long ticket descriptions for agents
- Draft a suggested reply grounded in similar past resolved tickets
- **Duplicate ticket detection** (keyword-overlap based) with one-click **merge**, closes the duplicate, links it to the primary ticket, and preserves both tickets' comment histories

### Enterprise Readiness
- **MFA / 2FA:** standard TOTP (RFC 6238), compatible with Google Authenticator/Authy, implemented with zero external dependencies (self-contained HMAC-SHA1 + Base32)
- **Outbound webhooks:** Slack/Discord/generic JSON delivery on SLA breach, admin-managed, per-webhook enable/disable and test-send
- **Ticket templates / custom fields per category:** admin-defined per-category fields (text/number/date/dropdown) rendered dynamically on the create-ticket form and shown on ticket detail
- Externalized secrets, CORS hardening, password policy, account lockout, rate limiting (Bucket4j), full security event/audit log

---

## Getting Started

### Environment Variables

Set these before running (IntelliJ: Run Configuration → Environment Variables), **never commit real values**:

| Variable | Purpose                                                                                                                                                                                           |
|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `JWT_SECRET` | Signing key for access/refresh tokens                                                                                                                                                             |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials for outbound notification emails                                                                                                                                                 |
| `MAIL_NOTIFY_EMAIL` | From-address for system emails                                                                                                                                                                    |
| `GROQ_API_KEY` | *(optional)* Enables AI-assisted triage (summarize / suggest reply). Free tier at [console.groq.com](https://console.groq.com) : no card required. Everything else in the app works without this. |

Relevant `application.properties` entries:
```properties
spring.jpa.hibernate.ddl-auto=update
app.base-url=http://localhost:8080
groq.api.key=${GROQ_API_KEY:}
groq.model=llama-3.3-70b-versatile
```

### Running Locally
1. Set the environment variables above.
2. Ensure a MySQL 8 instance is running and configured in `application.properties`.
3. Run the Spring Boot app — schema updates apply automatically on boot (`ddl-auto=update`), no manual migrations needed.
4. Open `http://localhost:8080`.

---

## Key Conventions

- All auth uses HttpOnly cookies with `credentials: 'include'`; `localStorage` retains only `jwt_username` and `jwt_role`
- `fetchWithAuth` (in `core.js`) handles silent token refresh via `/api/auth/refresh`, always use it instead of raw `fetch` for authenticated calls
- Frontend files are loaded as classic `<script>` tags (no ES modules), all functions/state are global. `core.js` must load first, `shortcuts-init.js` must load last. See `index.html`'s script-tag comment block and `ARCHITECTURE.md` for why.
- Native SQL queries can return `java.sql.Timestamp` instead of `LocalDateTime` depending on the JDBC driver, always normalize with an `instanceof` check rather than casting directly
- CI uses GitHub Actions with H2 (`MODE=MySQL;NON_KEYWORDS=VALUE`); `EmailService` is `@ConditionalOnProperty`-gated so CI doesn't fail without real SMTP credentials
- Presence tracking (collision detection) is in-memory and single-instance only, would need a shared store (Redis) if ever run behind a load balancer with multiple instances

---

## Roadmap Status

This project went through a full competitive gap-analysis roadmap comparing Ticketmaster against Zendesk/Freshdesk/JSM. Status:

| Phase | Item | Status                                                                                                                            |
|---|---|-----------------------------------------------------------------------------------------------------------------------------------|
| A | Business-hours SLA calendar | ✅ Done                                                                                                                            |
| A | Escalation policy on breach | ✅ Done                                                                                                                            |
| A | Avg First Response reporting bug fix | ✅ Done                                                                                                                            |
| B | Canned responses / macros | ✅ Done                                                                                                                            |
| B | Collision detection | ✅ Done                                                                                                                            |
| B | Skill/load-based auto-assignment | ✅ Done                                                                                                                            |
| C | Knowledge base module | ✅ Done                                                                                                                            |
| C | CSAT survey | ✅ Done                                                                                                                            |
| D | LLM-assisted triage (summarize, suggest reply) | ⏸️ Backend built, parked pending Groq API key setup                                                                               |
| D | Duplicate detection + merge | ✅ Done (keyword-based matching; upgradeable to semantic/embedding-based later)                                                    |
| E | MFA / 2FA | ✅ Done                                                                                                                            |
| E | Outbound webhooks | ✅ Done                                                                                                                            |
| E | Ticket templates / custom fields per category | ✅ Done                                                                                                                            |
| E | Email-to-ticket ingestion | 📋 Planned, requires a live IMAP mailbox to run/demo, unlike everything else, so it's scoped and documented rather than built now |


---

## Author

- P Sai Charan Reddy
- CSE AI

