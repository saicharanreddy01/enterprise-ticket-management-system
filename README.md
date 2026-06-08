# Enterprise Helpdesk Ticket Management System

A full-stack helpdesk ticketing application built with **Spring Boot 3** and **Vanilla JS**, featuring role-based access control, JPA auditing, and a responsive dashboard UI. Designed to simulate the internal support ticket workflows used in IT service firms.

---

## Features

- **Role-based access control (RBAC)** :- Developers can raise and view tickets; Admins can update and delete them
- **Ticket lifecycle management** :- Create, view, filter, resolve, and delete support tickets
- **Live dashboard** :- Real-time metrics: total tickets, open backlog, and resolved count
- **Priority levels** :- LOW, MEDIUM, HIGH with colour-coded badges
- **Audit trail** :- Every ticket records who raised it, who resolved it, and timestamps via JPA Auditing
- **Form validation** :- Server-side input validation with structured error responses
- **Custom exception handling** :- Global `@ControllerAdvice` for consistent API error responses
- **Session-based authentication** :- Custom login page with Spring Security form login

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3.4 |
| Security | Spring Security (RBAC, Form Login) |
| Persistence | Spring Data JPA, Hibernate, H2 (in-memory) |
| Validation | Jakarta Bean Validation (JSR-303) |
| Frontend | HTML5, Bootstrap 5.3, Vanilla JavaScript |
| Build | Maven |

---

## Project Structure

```
ticketmaster/
├── src/main/java/com/enterprise/ticketmaster/
│   ├── config/
│   │   └── SecurityConfig.java         # Spring Security + RBAC rules
│   ├── controller/
│   │   ├── TicketController.java        # REST endpoints for tickets
│   │   ├── UserController.java          # Current user info endpoint
│   │   └── WelcomeController.java       # Root health check
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java  # @ControllerAdvice error handler
│   │   └── ResourceNotFoundException.java
│   ├── model/
│   │   ├── Ticket.java                  # JPA entity with auditing
│   │   └── Priority.java               # Enum: LOW, MEDIUM, HIGH
│   ├── repository/
│   │   └── TicketRepository.java        # Spring Data JPA interface
│   ├── service/
│   │   └── TicketService.java           # Business logic layer
│   └── TicketmasterApplication.java     # Entry point + JPA Auditing enabled
├── src/main/resources/
│   ├── static/
│   │   ├── index.html                   # Main dashboard SPA
│   │   ├── login.html                   # Custom login page
│   │   ├── css/styles.css
│   │   └── js/app.js                    # Frontend logic
│   └── application.properties
└── pom.xml
```

---

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.8+

### Run the application

```bash
git clone https://github.com/saicharanreddy01/enterprise-ticket-management-system.git
cd enterprise-ticket-management-system/ticketmaster
./mvnw spring-boot:run
```

The app starts at `http://localhost:8080`

### Login credentials

| Role | Username | Password |
|---|---|---|
| Developer | `dev_user` | `dev123` |
| Admin | `admin_user` | `admin123` |

---

## API Endpoints

All endpoints require authentication. Role restrictions are enforced.

| Method | Endpoint | Role Required | Description |
|---|---|---|---|
| `GET` | `/api/tickets` | DEVELOPER, ADMIN | Fetch all tickets |
| `POST` | `/api/tickets` | DEVELOPER, ADMIN | Create a new ticket |
| `GET` | `/api/tickets/status/{status}` | DEVELOPER, ADMIN | Filter tickets by status |
| `PUT` | `/api/tickets/{id}` | ADMIN only | Update a ticket |
| `DELETE` | `/api/tickets/{id}` | ADMIN only | Delete a ticket |
| `GET` | `/api/users/me` | Authenticated | Get current user info |

### Example: Create a ticket

```http
POST /api/tickets
Content-Type: application/json

{
  "title": "Server RAM Upgrade Required",
  "description": "Production server needs upgrade to 32GB to prevent throttling.",
  "priority": "HIGH"
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
  "raisedBy": "dev_user",
  "resolvedBy": null,
  "createdAt": "2025-06-08T10:30:00",
  "updatedAt": "2025-06-08T10:30:00"
}
```

---

## Architecture

```
┌─────────────────────────────────────────────┐
│               Browser (Frontend)            │
│   login.html  ──►  index.html + app.js      │
└──────────────────────┬──────────────────────┘
                       │ HTTP / JSON
┌──────────────────────▼──────────────────────┐
│           Spring Boot Application           │
│                                             │
│  SecurityConfig (RBAC + Session Auth)       │
│         │                                   │
│  Controllers  ──►  Services  ──►  Repos     │
│         │                                   │
│  GlobalExceptionHandler (@ControllerAdvice) │
└──────────────────────┬──────────────────────┘
                       │ JPA / Hibernate
┌──────────────────────▼──────────────────────┐
│            H2 In-Memory Database            │
└─────────────────────────────────────────────┘
```

---

## Key Design Decisions

- **3-layer architecture** :- Controller, Service, and Repository layers are kept strictly separate, following standard Spring conventions
- **JPA Auditing** :- `@CreatedDate` and `@LastModifiedDate` are managed automatically via `@EnableJpaAuditing`, avoiding manual timestamp logic
- **Enum for Priority** :- Type-safe priority levels prevent invalid values reaching the database
- **Custom exceptions** :- `ResourceNotFoundException` maps to a clean 404 JSON response rather than a raw Spring error page
- **RBAC at the HTTP method level** :- `PUT` and `DELETE` are restricted to `ADMIN` role directly in `SecurityConfig`, not in business logic

---

## Planned Improvements

-  Replace H2 with MySQL/PostgreSQL for persistent storage
-  Add `Status` enum (OPEN, IN_PROGRESS, RESOLVED, CLOSED) to replace raw String
-  Switch from field injection (`@Autowired`) to constructor injection
-  Persist users in a database with a registration endpoint
-  Add unit and integration tests with MockMvc
-  Migrate credentials to environment variables / `application.properties`

---

## Author

**P. Sai Charan Reddy** — B.Tech CSE(AI), MITS (2027)  
Stanford University Innovation Fellow | MITS Student Council President  
[GitHub](https://github.com/saicharanreddy01)
