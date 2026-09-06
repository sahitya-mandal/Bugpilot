# BugPilot

AI-Powered Engineering Intelligence Platform for Bug Triage, Pull Request Review, and Repository Analytics.

---

## Overview

Modern software teams manage hundreds of GitHub issues, pull requests, and commit histories across distributed codebases. Engineering triage is often manual, repetitive, and fragmented across disparate developer tools.

**BugPilot** solves this problem by providing a centralized engineering intelligence workspace that connects to GitHub repositories, synchronizes engineering activity in the background, and leverages Large Language Models (Google Gemini) alongside deterministic heuristic engines to deliver automated bug triage, pull request risk analysis, and engineering analytics.

---

## Key Features

- **Stateless JWT Authentication**: Secure user registration and login with BCrypt password hashing and cryptographic JWT signature validation.
- **Role-Based Access Control (RBAC)**: Enforced authorization boundaries separating `ADMIN`, `DEVELOPER`, and `TESTER` roles, with admin-only user directory access.
- **Repository Import & Synchronization**: One-click import and on-demand synchronization of GitHub repositories, branches, issues, pull requests, and commits.
- **Strict Repository Ownership Isolation (BOLA/IDOR Prevention)**: All repository data, issues, pull requests, sync jobs, analytics, and timeline events are strictly isolated per authenticated user.
- **Asynchronous Background Synchronization**: Non-blocking `SyncJob` execution engine with active job state tracking, task rejection deadlock recovery, and application restart cleanup.
- **Resilient GitHub Integration**: Built-in exponential backoff retries with jitter for transient upstream failures, connect/read timeouts, and bounded pagination to prevent memory exhaustion.
- **Comprehensive Rate-Limit Handling**: Extracts GitHub `x-ratelimit-*` and `retry-after` headers, propagating exact reset timestamps to the UI.
- **AI Bug Triage (Google Gemini)**: Automated root-cause analysis, severity classification (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), affected architectural area detection, and suggested remediation steps.
- **AI Pull Request Code Review**: Automated PR risk level calculation (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), quality concern detection, potential bug identification, and merge recommendations.
- **AI Prompt-Injection Boundary**: Explicit security directives and XML boundary isolation (`<untrusted_github_data>`) protecting the AI analysis engine against prompt injection from untrusted external GitHub content.
- **Multi-Tier AI Fallback Engine**: Seamless 3-tier fallback architecture (Structured JSON $\to$ Legacy Section Tags $\to$ Deterministic Heuristic Triage) ensuring uninterrupted analysis during network partitions or upstream quota limits.
- **Repository Analytics & Timeline**: Real-time breakdown of issue severity distribution, PR risk levels, open/closed ratios, contributor metrics, and audit activity timeline.
- **Information Leakage Hardening**: Centralized exception handling returning generic HTTP 500 error responses (`"An unexpected error occurred"`), with internal details, database schemas, and stack traces logged only on the server with automated credential scrubbing.

---

## Architecture

BugPilot is structured as a decoupled client-server architecture:

```
+-------------------------------------------------------+
|                React 19 + TypeScript                  |
|          Vite + Material UI Dashboard (SPA)           |
+-------------------------------------------------------+
                           |
                     HTTPS / JSON
                           |
+-------------------------------------------------------+
|              Spring Boot 4 REST API                   |
|     (SecurityFilterChain + JwtAuthenticationFilter)   |
+-------------------------------------------------------+
                           |
       +-------------------+-------------------+
       |                                       |
+--------------+                       +---------------+
| Sync Engine  |                       |  AI Analysis  |
|  (Async Task |                       |   Service     |
|   Executor)  |                       +---------------+
+--------------+                               |
       |                                 Prompt Boundary
  REST Client                                  |
       |                               +---------------+
+--------------+                       | Google Gemini |
|  GitHub API  |                       | 1.5 Flash API |
+--------------+                       +---------------+
       |
+-------------------------------------------------------+
|             Spring Data JPA / Hibernate               |
+-------------------------------------------------------+
                           |
+-------------------------------------------------------+
|                  PostgreSQL Database                  |
+-------------------------------------------------------+
```

### High-Level Request Flow
1. **Interactive Requests**:
   `Frontend (Axios)` $\to$ `JWT Filter` $\to$ `Controller` $\to$ `Service` $\to$ `Repository` $\to$ `PostgreSQL`
2. **Background Synchronization Flow**:
   `API Request (/sync)` $\to$ `Create SyncJob (QUEUED)` $\to$ `TaskExecutor (Async)` $\to$ `GitHub API (Batched)` $\to$ `RepositorySyncPersistenceService` $\to$ `SyncJob (COMPLETED / FAILED)`
3. **AI Analysis Flow**:
   `User Request` $\to$ `Ownership Check` $\to$ `Build Untrusted Context Boundary` $\to$ `Gemini API Call (Propagation.NOT_SUPPORTED)` $\to$ `Multi-Tier Parser / Fallback` $\to$ `Save Analysis & Audit Event`

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Backend Runtime** | Java 21 (LTS) | Modern Java language platform |
| **Backend Framework** | Spring Boot 4.0.7 | Core application framework and DI |
| **Security** | Spring Security | Stateless security filter chain & method security |
| **Authentication** | JJWT (io.jsonwebtoken) | Cryptographic JWT token generation and verification |
| **Password Hashing** | BCrypt (Spring Security Crypto) | Strong adaptive password hashing |
| **Database** | PostgreSQL | Relational persistence store |
| **ORM / Persistence** | Spring Data JPA / Hibernate | Object-relational mapping and database abstraction |
| **Build Tool** | Apache Maven | Backend build lifecycle and dependency management |
| **Frontend Framework**| React 19 | Declarative component UI library |
| **Frontend Language** | TypeScript | Statically typed JavaScript |
| **Build & Bundler** | Vite | Lightning-fast frontend build tooling |
| **UI Component Suite**| Material UI (MUI) | Production-ready responsive UI component library |
| **HTTP Client** | Axios | Frontend API communication with interceptors |
| **AI Integration** | Google Gemini 1.5 Flash | Large Language Model engineering intelligence |
| **Version Control API**| GitHub REST API v3 | Repository, Issue, PR, and Commit data ingestion |

---

## Project Structure

```
bugpilot/
├── pom.xml                                  # Maven backend dependencies and build plugins
├── mvnw / mvnw.cmd                          # Maven wrapper binaries
├── README.md                                # Project documentation
├── .gitignore                               # Global Git exclusion rules
├── src/
│   ├── main/
│   │   ├── java/com/bugpilot/
│   │   │   ├── config/                      # SecurityFilterChain, AsyncConfig, JWT filter
│   │   │   ├── controller/                  # REST controllers (Auth, Repos, Issues, PRs, etc.)
│   │   │   ├── dto/                         # Request and response data transfer objects
│   │   │   ├── entity/                      # JPA entities (User, Repo, Issue, PR, Commit, etc.)
│   │   │   ├── enums/                       # Domain enums (Severity, Risk, SyncStatus, etc.)
│   │   │   ├── exception/                   # GlobalExceptionHandler and custom exceptions
│   │   │   ├── repository/                  # Spring Data JPA repositories
│   │   │   ├── security/                    # UserDetailsService, EntryPoint, AccessDeniedHandler
│   │   │   └── service/                     # Business logic (AI, Sync, GitHub, Auth, etc.)
│   │   └── resources/
│   │       └── application.properties       # Spring Boot application configuration
│   └── test/
│       └── java/com/bugpilot/               # Automated unit and integration test suite (316 tests)
└── frontend/
    ├── package.json                         # Frontend dependencies and scripts
    ├── vite.config.ts                       # Vite configuration
    ├── tsconfig.json                        # TypeScript compiler options
    ├── index.html                           # Single-page application HTML entrypoint
    ├── .env.example                         # Environment configuration template
    ├── public/                              # Static public assets (favicons, SVG icons)
    └── src/
        ├── App.tsx                          # Root router and layout container
        ├── main.tsx                         # React DOM bootstrap
        ├── theme.ts                         # Custom dark Material UI theme definition
        ├── assets/                          # Application illustrations and imagery
        ├── components/                      # Reusable UI widgets (modals, chips, cards, states)
        ├── context/                         # Authentication context and session state
        ├── layouts/                         # App navigation layout and top app bar
        ├── pages/                           # Screen views (Dashboard, Repositories, Details, etc.)
        ├── services/                        # Axios API service clients
        └── types/                           # TypeScript domain interfaces and types
```

---

## Getting Started

### Prerequisites
- **Java**: Java 21 LTS installed and on `PATH`
- **Node.js**: Node.js 18+ and `npm` installed
- **PostgreSQL**: PostgreSQL 14+ running locally (default: `localhost:5432`)
- **Git**: Git installed

---

### Backend Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/sahitya-mandal/Bugpilot.git
   cd Bugpilot
   ```

2. **Configure PostgreSQL**:
   Create a database named `bugpilot` in your local PostgreSQL instance:
   ```sql
   CREATE DATABASE bugpilot;
   ```

3. **Set required environment variables**:
   Configure environment variables in your terminal session (never commit real secrets):
   
   *Windows (PowerShell)*:
   ```powershell
   $env:DB_PASSWORD="your_postgres_password"
   $env:JWT_SECRET="your_jwt_secret_key_minimum_256_bits_for_hmac_sha256"
   $env:GITHUB_TOKEN="your_github_personal_access_token"  # Optional, increases rate limits
   $env:GEMINI_API_KEY="your_google_gemini_api_key"        # Optional, enables LLM triage
   ```

   *Linux / macOS (Bash)*:
   ```bash
   export DB_PASSWORD="your_postgres_password"
   export JWT_SECRET="your_jwt_secret_key_minimum_256_bits_for_hmac_sha256"
   export GITHUB_TOKEN="your_github_personal_access_token"
   export GEMINI_API_KEY="your_google_gemini_api_key"
   ```

4. **Run the backend**:
   ```bash
   # Windows
   .\mvnw.cmd spring-boot:run

   # Linux / macOS
   ./mvnw spring-boot:run
   ```
   The backend will start on `http://localhost:8080`.

---

### Frontend Setup

1. **Navigate to the frontend directory**:
   ```bash
   cd frontend
   ```

2. **Configure environment**:
   Copy the environment template:
   ```bash
   # Windows (PowerShell)
   Copy-Item .env.example .env

   # Linux / macOS
   cp .env.example .env
   ```
   *(Ensure `VITE_API_BASE_URL=http://localhost:8080`)*

3. **Install dependencies and start development server**:
   ```bash
   npm install
   npm run dev
   ```
   The frontend will start on `http://localhost:5173`.

---

## Environment Variables

| Variable | Required | Purpose | Example Placeholder |
|---|---|---|---|
| `DB_PASSWORD` | **Yes** | Password for PostgreSQL user `postgres` | `your_database_password` |
| `JWT_SECRET` | **Yes** | HMAC-SHA256 secret key for signing JWTs | `your_jwt_secret_key_minimum_256_bits` |
| `JWT_EXPIRATION` | No | JWT validity in milliseconds (default: `3600000` / 1h) | `3600000` |
| `GITHUB_TOKEN` | Recommended | GitHub PAT to raise rate limits from 60 to 5,000 req/hr | `your_github_token` |
| `GITHUB_API_URL` | No | Base URL for GitHub REST API (default: `https://api.github.com`) | `https://api.github.com` |
| `GITHUB_API_CONNECT_TIMEOUT` | No | Connection timeout in milliseconds (default: `10000`) | `10000` |
| `GITHUB_API_READ_TIMEOUT` | No | Read timeout in milliseconds (default: `30000`) | `30000` |
| `GITHUB_API_MAX_RETRIES` | No | Maximum retry attempts for transient errors (default: `2`) | `2` |
| `GITHUB_API_RETRY_BACKOFF_MS`| No | Exponential backoff delay base in milliseconds (default: `500`) | `500` |
| `GEMINI_API_KEY` | Recommended | Google Gemini API key for AI analysis | `your_gemini_api_key` |
| `GEMINI_API_MODEL` | No | Gemini model identifier (default: `gemini-1.5-flash`) | `gemini-1.5-flash` |
| `VITE_API_BASE_URL` | Frontend | Backend REST API base URL | `http://localhost:8080` |

> [!IMPORTANT]
> Never commit `.env` files or secret values to version control. All `.env` and `*.env` files are excluded by `.gitignore`.

---

## API Overview

All protected endpoints require an `Authorization: Bearer <token>` HTTP header.

### 1. Authentication (`/auth`)
- `POST /auth/register` — Register a new account (`name`, `email`, `password`, `role`).
- `POST /auth/login` — Authenticate credentials and receive JWT access token.

### 2. User Administration (`/users` — ADMIN Only)
- `GET /users` — List all registered users across the platform.
- `GET /users/{id}` — Retrieve user profile by identifier.
- `POST /users` — Create a new user record.
- `PUT /users/{id}` — Update user details or role.
- `DELETE /users/{id}` — Delete user account.

### 3. Repositories (`/repositories`)
- `GET /repositories` — List all registered repositories for authenticated user.
- `GET /repositories/{id}` — Retrieve repository details by identifier.
- `POST /repositories` — Register an existing GitHub repository.
- `DELETE /repositories/{id}` — Remove repository and cascade delete associated data.

### 4. Background Synchronization (`/repositories/{id}/sync`, `/sync-jobs`)
- `POST /repositories/{id}/sync` — Queue asynchronous GitHub sync job for repository.
- `POST /repositories/import` — Atomic repository registration and sync queuing.
- `GET /sync-jobs/{id}` — Poll status and progress step of a specific sync job.
- `GET /repositories/{id}/sync-jobs` — Retrieve sync execution history for a repository.
- `GET /repositories/{id}/sync-jobs/active` — Check for active sync job in progress.

### 5. Issues & AI Triage (`/repositories/{id}/issues`, `/issues`)
- `GET /repositories/{id}/issues` — List all synchronized GitHub issues.
- `GET /issues/{id}` — Retrieve detailed issue record.
- `POST /issues/{id}/analyze` — Trigger AI bug triage analysis on the issue.
- `GET /issues/{id}/analysis` — Retrieve existing AI bug analysis report.

### 6. Pull Requests & AI Review (`/repositories/{id}/pull-requests`, `/pull-requests`)
- `GET /repositories/{id}/pull-requests` — List synchronized pull requests.
- `GET /pull-requests/{id}` — Retrieve detailed pull request record.
- `POST /pull-requests/{id}/analyze` — Trigger AI code review analysis on the PR.
- `GET /pull-requests/{id}/analysis` — Retrieve existing AI PR analysis report.

### 7. Analytics & Timeline (`/repositories/{id}/analytics`, `/repositories/{id}/activities`)
- `GET /repositories/{id}/analytics` — Aggregated metrics (severity distributions, risk breakdown, contributors).
- `GET /repositories/{id}/activities` — Chronological audit timeline of sync and AI actions.

### 8. System Health (`/hello`)
- `GET /hello` — Public health verification endpoint.

---

## Security Model

- **Stateless Authentication**: Sessions are managed purely via signed JWT tokens with expiration validation.
- **Credential Storage**: Passwords are encrypted using BCrypt before database persistence; raw passwords are never stored.
- **Strict Authorization**: Filter-level authorization (`SecurityConfig`) complemented by method-level security (`@PreAuthorize`).
- **Repository Isolation (BOLA / IDOR Protection)**: Every data access layer call enforces ownership against `SecurityContextHolder.getContext().getAuthentication()`. Accessing another user's repository triggers HTTP 403 Forbidden.
- **User Directory Protection**: `/users` and `/users/**` endpoints are restricted strictly to `ROLE_ADMIN`. Normal `DEVELOPER` and `TESTER` users receive HTTP 403.
- **AI Prompt-Injection Defense**: External GitHub data is enclosed in dedicated XML boundary delimiters (`<untrusted_github_data>`) with system directives commanding the model to treat all external text strictly as data.
- **Information Leakage Prevention**: Fallback error handling maps all unhandled exceptions to a generic safe response (`"An unexpected error occurred"`). Stack traces, SQL syntax, table names, and filesystem paths are never returned to clients.
- **Safe Logging**: Server logs sanitize Bearer tokens, GitHub PATs, and passwords before recording unhandled exceptions.

---

## Testing

BugPilot features an automated test suite verifying security filters, RBAC rules, controllers, service orchestration, GitHub rate-limit parsing, prompt injection boundaries, and concurrency handling.

### Running Backend Tests
```bash
# Run unit, controller, service, security, and mock integration tests (316 tests)
# (Does not require a running PostgreSQL instance)
.\mvnw.cmd test "-Dtest=*Test,!BugpilotBackendApplicationTests"

# Run complete test suite (requires local PostgreSQL running on localhost:5432)
.\mvnw.cmd test
```

### Running Frontend Verification
```bash
cd frontend

# Verify TypeScript types and execute production build
npm run build
```

---

## Current Status

BugPilot is under active development as a portfolio and demonstration engineering platform. The backend REST architecture, asynchronous sync engine, AI triage pipelines, security boundaries, and responsive frontend dashboard are fully implemented and verified with 316 automated tests.

---

## Roadmap

Planned future enhancements for upcoming iterations:
- **GitHub OAuth 2.0 Web Flow**: User-specific GitHub OAuth authorization replacing server-level token configuration.
- **Automated Database Migrations**: Integration of Liquibase / Flyway for automated schema evolution.
- **Containerization**: `docker-compose.yml` providing one-command local evaluation with PostgreSQL, backend, and frontend containers.
- **Continuous Integration**: GitHub Actions automated pipeline executing Maven test runs and Vite builds on push and pull request.
- **Frontend Code Splitting**: Route-based dynamic code splitting (`React.lazy`) to optimize JavaScript bundle size.

---

## Contributing

Contributions, bug reports, and feature proposals are welcome.

1. Fork the repository.
2. Create a feature branch:
   ```bash
   git checkout -b feat/your-feature-name
   ```
3. Commit your changes following conventional commit syntax (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`).
4. Verify all tests pass:
   ```bash
   .\mvnw.cmd test "-Dtest=*Test,!BugpilotBackendApplicationTests"
   ```
5. Push to your fork and submit a Pull Request.

---

## License

This project is licensed under the terms of the [MIT License](LICENSE).  
Copyright (c) 2026 SAHITYA.
