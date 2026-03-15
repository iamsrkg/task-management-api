# Secure Multi-Tenant Task Management API

A robust, production-ready REST API built with Spring Boot 3, demonstrating advanced backend architecture, security, and data integrity practices. Built over a 10-day sprint, this application serves as a comprehensive backend for managing hierarchical projects and tasks with strict multi-tenant data isolation.

## 🚀 Features

*   **Multi-Tenant Architecture**: Users only see, manage, and interact with the Projects and Tasks they own or are assigned to.
*   **JWT Authentication**: Stateless, secure login system using JSON Web Tokens and BCrypt password hashing.
*   **Role-Based Access Control (RBAC)**: Fine-grained method-level security (`@PreAuthorize`) differentiating standard `USER` functionalities from administrative `ADMIN` endpoints.
*   **Optimistic Data Locking**: Employs JPA `@Version` to prevent the "Lost Update Problem" (concurrent data overrides) gracefully with `409 Conflict` resolution.
*   **Clean API Response Uniformity**: A global `ApiResponse<T>` wrapper guarantees consistent, predictable JSON structures for all successes and errors.
*   **Robust Exception Handling**: A `@RestControllerAdvice` safety net catches Bean Validation errors (`@NotBlank`, etc.), missing resources (`404`), authorization breaches (`403`), and concurrency conflicts (`409`), preventing dirty stack-traces from leaking.
*   **Cursor/Pageable Support**: Native pagination and sorting for large data tables.
*   **Dockerized**: Employs a multi-stage Dockerfile and Docker Compose orchestration for a guaranteed, consistent runtime environment.

---

## 🛠️ Technology Stack

*   **Java 21**
*   **Spring Boot 3.2+** (Web, Data JPA, Security, Validation)
*   **PostgreSQL 15** (Relational Database)
*   **Hibernate** (ORM & JPA Implementation)
*   **Docker & Docker Compose**
*   **JJWT** (JSON Web Tokens)

---

## 🏃 Getting Started (Quickstart)

Thanks to Docker, you don't need Java, Maven, or PostgreSQL installed locally to run this API!

### Prerequisites
*   [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running.

### Booting the Application
1. Clone the repository and root into it:
   ```bash
   git clone https://github.com/iamsrkg/task-management-api.git
   cd task-management-api
   ```
2. Build and boot the stack:
   ```bash
   docker compose up --build -d
   ```
3. The API is now running on `localhost:8080`.
   *(To view the logs, run: `docker compose logs -f api`)*

---

## 📖 API Documentation Overview

All endpoints (except auth) require a valid `Authorization: Bearer <token>` header.

### 1. Authentication
*   **`POST /api/auth/register`**: Register a new user (`email`, `password`).
*   **`POST /api/auth/login`**: Authenticate and receive a JWT token.

### 2. Users & Roles
*   **`GET /api/users/me`**: Get the current authenticated user's profile.
*   **`GET /api/admin/users`**: List all users. *(Requires ADMIN role).* The application automatically seeds an admin user (`admin@example.com` / `admin123`) on startup.

### 3. Projects (Multi-Tenant)
*   **`POST /api/projects`**: Create a new project.
*   **`GET /api/projects`**: List all projects owned by the authenticated user.

### 4. Tasks (Includes Pagination & Filtering)
*   **`POST /api/tasks`**: Create a task within an owned project.
*   **`GET /api/tasks?status=TODO&page=0&size=10&sort=createdAt,desc`**: Get tasks (paginated) for the user. Supports optional `status` filtering.
*   **`PUT /api/tasks/{taskId}`**: Overwrite a task. *(Requires `version` in payload for Optimistic Locking).*
*   **`PATCH /api/tasks/{taskId}/status`**: Update the status string of a task.
*   **`DELETE /api/tasks/{taskId}`**: Delete an owned task.
