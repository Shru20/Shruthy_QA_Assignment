# GitLab Issues API Test Automation Project

Automated API test suite for the [GitLab Issues API](https://docs.gitlab.com/ee/api/issues.html),
covering CRUD operations, edge cases, and negative scenarios. Built with **Java 17**,
**JUnit 5**, **REST Assured**, with **Allure** reporting and a **GitHub Actions** CI pipeline.

---

## 1. Solution Overview

The suite exercises the GitLab Issues REST API directly (no GitLab-specific client library),
validating status codes and response payloads for the full issue lifecycle.

**What is covered**

| Area | Class | Examples |
|------|-------|----------|
| Create | `IssueCreateTests` | title only, description, labels, special/HTML/unicode content, empty title |
| Read | `IssueReadTests` | get by IID, list, state filters, field presence, 404 handling |
| Update | `IssueUpdateTests` | title/description/labels, close, reopen, multi-field update |
| Delete | `IssueDeleteTests` | delete, delete non-existent, verify deletion, bulk delete |
| Edge / Negative | `IssueEdgeCaseTests` | invalid project id, missing auth, invalid state, boundary inputs |

**Design principles**
- **Layered architecture** — tests never build raw requests; they call a reusable API client.
- **Clean configuration** — secrets are read from environment variables at runtime; nothing
  confidential is committed.
- **Readable tests** — each test has an Allure `@Feature`/`@Description` and a `@DisplayName`.
- **Deterministic data** — random-but-valid test data via JavaFaker.

---

## 2. Architecture

```
src/test/java/com/abnamro/assignment/
├── base/        BaseTest            – REST Assured setup, client wiring, fail-fast config checks
├── client/      GitLabIssuesClient  – all Issues API calls (create/read/update/delete/close/reopen)
├── config/      ApiConfig           – base URL, token, project id, REST Assured request spec
│                TestConfiguration   – layered config resolver (env > sys prop > properties > default)
├── dto/         IssueRequestDto     – request payload model (plain POJO + builder)
├── mapper/      IssueMapper         – Issue <-> IssueRequestDto mapping
├── model/       Issue, User, Milestone, TimeStats, TaskCompletionStatus – response models
├── provider/    TestDataProvider    – Faker-based data generation
├── tests/       Issue*Tests         – JUnit 5 test classes (tagged CREATE/READ/UPDATE/DELETE/EDGE_CASE)
└── util/        ApiAssertions       – reusable status/body assertions
                 TestDataGenerator   – simple Faker helpers
src/test/resources/
├── application.properties           – config keys with ${ENV_VAR:default} placeholders
└── logback-test.xml                 – console logging
```

**Request flow:** `Test` → `GitLabIssuesClient` → `ApiConfig.getRequestSpec()` (base URI +
`PRIVATE-TOKEN` header + JSON) → REST Assured → GitLab API → response parsed via Gson into models.

---

## 3. Tech Stack

| Purpose | Library | Version |
|---------|---------|---------|
| Language | Java | 17 |
| Build | Maven | 3.6+ |
| Test runner | JUnit 5 (Jupiter) | 5.10.0 |
| HTTP / assertions | REST Assured | 5.4.0 |
| JSON | Gson / Jackson | 2.10.1 / 2.15.3 |
| Fluent assertions | AssertJ | 3.24.1 |
| Test data | JavaFaker | 1.0.2 |
| Reporting | Allure (JUnit5 + REST Assured) | 2.25.0 |
| Logging | SLF4J + Logback | 2.0.9 / 1.4.14 |

> Note: models are plain POJOs (no Lombok/MapStruct annotation processing) to keep the build
> robust across JDKs.

---

## 4. Prerequisites

- **JDK 17** (build targets Java 17 bytecode via `--release 17`).
- **Maven 3.6+**.
- A **GitLab account**, a **Personal Access Token** with the `api` scope, and a **project ID**
  where issues can be freely created/updated/deleted (use a dedicated throwaway project).
- Network access to `https://gitlab.com/api/v4` (or your self-hosted instance).

---

## 5. Configuration

Secrets are **not** stored in the repository. `application.properties` references environment
variables with optional fallbacks:

```properties
api.base.url=${GITLAB_API_BASE_URL:https://gitlab.com/api/v4}
api.token=${GITLAB_API_TOKEN}
api.project.id=${GITLAB_API_PROJECT_ID}
```

**Resolution priority** (highest first): environment variable → JVM system property →
`application.properties` (with `${ENV:default}` expansion) → built-in default.

| Setting | Environment variable | Required | Default |
|---------|----------------------|----------|---------|
| API base URL | `GITLAB_API_BASE_URL` | No | `https://gitlab.com/api/v4` |
| Access token | `GITLAB_API_TOKEN` | **Yes** | – |
| Project ID | `GITLAB_API_PROJECT_ID` | **Yes** | – |

`BaseTest` performs a **fail-fast** check and throws a clear error if the token or project id
is missing (prevents confusing 404s).

### Setting variables locally

Windows PowerShell:
```powershell
$env:GITLAB_API_TOKEN = "glpat-xxxxxxxxxxxxxxxx"
$env:GITLAB_API_PROJECT_ID = "12345678"
```

Linux/macOS:
```bash
export GITLAB_API_TOKEN="glpat-xxxxxxxxxxxxxxxx"
export GITLAB_API_PROJECT_ID="12345678"
```

In **IntelliJ IDEA**: Run → Edit Configurations → *Environment variables* →
`GITLAB_API_TOKEN=...;GITLAB_API_PROJECT_ID=...` (set it on the JUnit template so all runs inherit it).

Verify credentials quickly:
```bash
curl -H "PRIVATE-TOKEN: $GITLAB_API_TOKEN" "https://gitlab.com/api/v4/projects/$GITLAB_API_PROJECT_ID"
```

---

## 6. Build & Run

Compile only:
```bash
mvn -B clean test-compile
```

Run all tests:
```bash
mvn -B clean test
```

Run a single class or method:
```bash
mvn test -Dtest=IssueCreateTests
mvn test -Dtest=IssueCreateTests#testCreateIssueWithTitleOnly
```

Run by tag (CRUD areas are tagged):
```bash
mvn test -Dgroups="CREATE"
mvn test -Dgroups="EDGE_CASE"
```

> Tests run in parallel (Surefire `parallel=methods`, `threadCount=4`).

---

## 7. Allure Report

Generate the HTML report after a run:
```bash
mvn allure:report      # output: target/site/allure-maven-plugin
mvn allure:serve       # generate and open in a browser
```

---

## 8. Continuous Integration (GitHub Actions)

Workflow: `.github/workflows/tests.yml`. On push/PR to `main`/`master`/`develop` (and manual
dispatch) it:

1. Sets up **JDK 17** with Maven caching.
2. Verifies required secrets are present (fails fast otherwise).
3. Injects secrets as env vars and runs `mvn -B clean test` — values are read at runtime via
   `System.getenv(...)`.
4. Generates the **Allure HTML report** and uploads artifacts (`allure-results`,
   `allure-report`, `surefire-reports`).
5. **Publishes the Allure report to GitHub Pages** for a browsable per-run link.

### CI setup (one time)
1. Repo **Settings → Secrets and variables → Actions** → add:
   - `GITLAB_API_TOKEN`
   - `GITLAB_API_PROJECT_ID`
   - *(optional)* `GITLAB_API_BASE_URL` (self-hosted GitLab only)
2. Repo **Settings → Pages** → **Source = GitHub Actions**.
3. Push — the report link appears on the *Publish Allure Report to Pages* job.

> Secrets are **not** available to workflows triggered by pull requests from forks (GitHub
> security), so Pages publishing effectively applies to same-repo pushes.

---

## 9. Assumptions & Trade-offs

- **Live integration tests.** Tests hit the real GitLab API and create/modify/delete real
  issues — use a dedicated project. There is no mock server.
- **State changes use `state_event`.** GitLab's edit endpoint changes state via
  `state_event=close|reopen` (not `state`); the client maps `closed/opened` accordingly.
- **Unauthenticated requests may return 404, not 401.** GitLab hides private resources, so the
  negative auth test accepts either 401 or 404.
- **No Lombok/MapStruct processors.** Models are plain POJOs for a portable, JDK-agnostic build.
- **Java 17 bytecode.** If only JDK 21 is installed locally, compilation still targets 17 via
  `--release 17`; point `JAVA_HOME` to a JDK 17 runtime if you need to *run* Maven on 17.
- **Test isolation.** Each test creates its own data; there is no shared fixture cleanup, so a
  disposable project keeps things tidy.

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `IllegalStateException: project id not configured` | env vars not set for the run | set `GITLAB_API_TOKEN` / `GITLAB_API_PROJECT_ID` |
| 404 on every request | wrong/empty project id or path id used | use the **numeric** project id; verify token access |
| 401 on requests | invalid/expired token or missing `api` scope | regenerate token with `api` scope |
| `logback` XML parse error | corrupted `logback-test.xml` | ensure the file contains valid XML |
| Allure report empty | tests didn't produce results | run `mvn clean test` before `allure:report` |
