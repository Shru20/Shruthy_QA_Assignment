# GitLab Issues API Test Automation

Automated API test suite for the [GitLab Issues API](https://docs.gitlab.com/ee/api/issues.html),
covering CRUD operations, boundary conditions and unusual inputs. Built with **Java 17**,
**JUnit 5**, **REST Assured** and **AssertJ**, with **Allure** reporting and a **GitHub Actions**
CI pipeline.

The API is exercised directly over HTTP. No GitLab client library is used — the API itself is the
system under test, so an abstraction over it would test the wrong thing.

---

## 1. Findings

Three behaviours were found where the API accepts input it cannot honour and reports success.
Each is pinned by a test so a future change is detected rather than assumed.

### 1.1 Lenient `due_date` parsing — *highest impact*

`PUT /projects/:id/issues/:issue_iid` accepts `due_date` values outside the documented ISO-8601
(`YYYY-MM-DD`) format and coerces them.

| Input | Stored | Status |
|---|---|---|
| `31-12-2026` | `2026-12-31` | 200 |
| `01-02-2026` | `2026-02-01` | 200 |

The second case is the concern. `01-02-2026` is valid under both `DD-MM-YYYY` and `MM-DD-YYYY`;
the API resolves it day-first. A client following US convention and intending **2 January**
silently stores **1 February**, with nothing in the response to signal the misinterpretation.

Strict validation would convert a silent data-correctness bug into an immediate, actionable 400.
Covered by **TC216–TC218** (`IssueUpdateTests`).

### 1.2 Over-long labels are silently discarded

`POST /projects/:id/issues` accepts a label exceeding the 255-character limit, returns **201
Created**, and omits it entirely — the issue comes back with an empty `labels` array.

| Behaviour | Client can detect? | Intent preserved? |
|---|---|---|
| Reject with 400 | Yes | n/a — client corrects and retries |
| Truncate to 255 | Yes, by inspecting the response | Partially |
| **Silent discard (actual)** | **Only by diffing request against response** | **No** |

The impact falls on automation rather than humans. A pipeline labelling issues for triage
routing or SLA tracking receives a 201 and assumes success; the issue is then invisible to every
query filtering on that label, and no retry logic triggers.

Covered by **TC407** (`IssueEdgeCaseTests`).

### 1.3 Titles are not normalised for interior whitespace

Leading and trailing whitespace is trimmed, but interior newlines are stored verbatim in a field
that is single-line in every rendering context.

| Input | Stored |
|---|---|
| `"  Title  "` | `"Title"` |
| `"First line\nSecond line"` | unchanged, newline intact |

HTML collapses the newline so the UI looks correct. It surfaces in plain-text consumers — CSV
export, webhook payloads, notification subjects, CLI output — and in exact-match search, where
two titles differing only by an invisible newline will not compare equal.

Lower severity than the first two: no data is misinterpreted, only stored in a shape the field's
usage does not anticipate. Covered by **TC401** and **TC405**.

### 1.4 The pattern

Query parameters governing request mechanics are validated strictly — `per_page=0` is refused
with a clear 400 (**TC416**). Body fields carrying user content are accepted and adapted. The
inconsistency is the substantive observation: a developer who learns from the `per_page`
behaviour that GitLab validates input will reasonably assume the same of `labels`, and be wrong
in a way that costs them data.

---

## 2. Coverage

| Area | Class | Tests       | Focus |
|---|---|-------------|---|
| Create | `IssueCreateTests` | TC001–TC008 | mandatory fields, labels, HTML content, duplicate titles, 255-char boundary either side, empty and whitespace-only titles |
| Read | `IssueReadTests` | TC040–TC059 | single fetch, response contract, filters by state and label, pagination, page overlap, default sort, invalid identifiers |
| Update | `IssueUpdateTests` | TC060–TC081 | field updates, partial-update safety, state transitions and idempotency, `labels` vs `add_labels` vs `remove_labels`, `due_date` handling |
| Delete | `IssueDeleteTests` | TC009–TC022 | deletion and verification, double-delete semantics, iid non-reuse, auth refusal with no side effect |
| Edge cases | `IssueEdgeCaseTests` | TC023–TC039 | Unicode and emoji, script and SQL-like input, large payloads, label boundaries, malformed JSON, pagination limits, confidentiality |

**What these tests are built to catch,** beyond status codes:

- **Partial-update clobbering** (TC203) — a title-only update must not clear the description or
  labels. Every field-specific test would still pass against an API that wiped everything else.
- **Echo versus persistence** (TC205, TC402, TC403) — values are re-read through a separate GET,
  since a write response reporting on itself proves nothing.
- **Refusal with no side effect** (TC312, TC313, TC223) — a 401 alone shows what the API said,
  not what it did; the resource is checked afterwards.
- **Filter exclusion, not just inclusion** (TC108, TC109) — a filter that is silently ignored
  returns everything and would satisfy a one-sided check.
- **Page overlap** (TC113) — an off-by-one in the offset produces pages that each look correct
  in isolation.

**Deliberately excluded:** tests using the `search` parameter. GitLab's full-text search is not
guaranteed read-your-writes, so asserting on freshly created data through it invites intermittent
failure. TC107 covers the same intent deterministically via the indexed `iids[]` filter.

---

## 3. Architecture

```
src/test/java/com/abnamro/assignment/
├── base/     BaseTest            – config validation, client wiring, automatic issue cleanup
├── client/   GitLabIssuesClient  – every Issues API call, including no-auth and
│                                   wrong-project variants used by negative tests
├── config/   ApiConfig           – typed façade; builds the three request specifications
│             TestConfiguration   – sole reader of config sources (env > sysprop > file > default)
├── model/    Issue               – response model, read-only
├── tests/    Issue*Tests         – tagged CREATE / READ / UPDATE / DELETE / EDGE_CASE
└── util/     ApiAssertions       – transport-level assertions with diagnostic failure messages
                                    (status, headers, JSON fields, list sizes)
           TestDataGenerator      – unique-by-construction titles and labels

src/test/resources/
├── application.properties        – non-sensitive settings only
└── logback-test.xml              – console logging, level overridable via -Dlog.level
```

**Request flow:** test → `GitLabIssuesClient` → `ApiConfig.getRequestSpec()` (base URI, auth
header, JSON) → REST Assured → GitLab → Gson → `Issue`.

**Three decisions worth noting:**

- **Uniqueness is a correctness requirement, not a convenience.** Every generated title and
  label carries a random suffix. Label-filter tests assert an exact result count; without
  guaranteed uniqueness they would fail intermittently against data left by earlier runs.
- **No-auth and wrong-project request variants live in the client**, not assembled ad hoc in
  tests, so a negative-path request differs from a positive one only in the dimension under
  test — never in base URI, content type or filters.
- **Setup failures throw; behaviour failures assert.** A test that cannot establish its
  preconditions has errored, not failed, and conflating the two sends the reader hunting for a
  defect that is really a configuration problem.

---

## 4. Tech stack

| Purpose | Library | Version |
|---|---|---|
| Language | Java | 17 |
| Build | Maven | 3.6+ |
| Test runner | JUnit 5 | 5.10.2 |
| HTTP client | REST Assured | 5.4.0 |
| Assertions | AssertJ | 3.25.3 |
| JSON | Gson | 2.10.1 |
| Test data | JavaFaker | 1.0.2 |
| Reporting | Allure JUnit 5 | 2.25.0 |
| Logging | SLF4J + Logback | 2.0.12 / 1.4.14 |

`allure-rest-assured` is deliberately **not** included. Its filter attaches full request headers
to the report, which would publish the API token to the GitHub Pages site. The `@Step`
annotations on the client give per-call visibility without that exposure.

Models are plain POJOs — no Lombok or MapStruct — so the build needs no annotation processing.

---

## 5. Prerequisites

- **JDK 17** (build targets Java 17 bytecode via `--release 17`).
- **Maven 3.6+**.
- A **GitLab account** and a token with the `api` scope.
- A **dedicated, disposable GitLab project**. Tests create, modify and delete real issues.
- **Owner role on that project.** Issue deletion requires it; a Maintainer token fails every
  test in `IssueDeleteTests` with 403/404. That is a configuration problem, not a product defect.
- Network access to `https://gitlab.com/api/v4`, or your self-hosted instance.

---

## 6. Configuration

Credentials come from the environment. Nothing sensitive is committed.

**Resolution order per setting:** environment variable → JVM system property →
`application.properties` → built-in default. The variable name is derived from the key:
`api.project.id` → `GITLAB_API_PROJECT_ID`.

| Setting | Environment variable | Required | Default |
|---|---|---|---|
| Access token | `GITLAB_API_TOKEN` | **Yes** | – |
| Project ID | `GITLAB_API_PROJECT_ID` | **Yes** | – |
| API base URL | `GITLAB_API_BASE_URL` | No | `https://gitlab.com/api/v4` |
| Auth scheme | `GITLAB_API_AUTH_SCHEME` | No | `oauth2` |

**Auth scheme** must match the credential type. `oauth2` sends `Authorization: Bearer`;
`private-token` sends the `PRIVATE-TOKEN` header. A personal access token (prefix `glpat-`)
needs `private-token`, or every request returns 401.

`BaseTest` fails fast with a targeted message if the token or project id is missing, rather than
letting the suite produce dozens of 401s that look like product defects.

**Linux / macOS**
```bash
export GITLAB_API_TOKEN="glpat-xxxxxxxxxxxxxxxx"
export GITLAB_API_PROJECT_ID="12345678"
export GITLAB_API_AUTH_SCHEME="private-token"
```

**Windows PowerShell**
```powershell
$env:GITLAB_API_TOKEN = "glpat-xxxxxxxxxxxxxxxx"
$env:GITLAB_API_PROJECT_ID = "12345678"
$env:GITLAB_API_AUTH_SCHEME = "private-token"
```

**IntelliJ IDEA** — Run → Edit Configurations → Templates → JUnit → *Environment variables*, so
every run inherits them.

**Verify before running the suite**
```bash
curl -H "PRIVATE-TOKEN: $GITLAB_API_TOKEN" \
     "https://gitlab.com/api/v4/projects/$GITLAB_API_PROJECT_ID"
```

---

## 7. Running the tests

```bash
mvn clean test                                          # everything
mvn test -Dtest=IssueCreateTests                        # one class
mvn test -Dtest=IssueUpdateTests#testCloseIssue         # one method
mvn test -Dgroups="CREATE"                              # by tag
mvn test -Dgroups="Smoke"                               # smoke subset
mvn test -Dlog.level=DEBUG                              # verbose request logging
```
Tags: `CREATE`, `READ`, `UPDATE`, `DELETE`, `EDGE_CASE`, `Smoke`.

``` GitHub Actions CI
Navigate to the repository → Actions → *New workflow* → On the left side bar *GitLab Issues API Tests* -> Use workflow from 
(Choose branch as `master`) → Run workflow. The workflow runs the suite.
Allure report link will be available in the workflow summary.
```

**Execution is sequential by design.** Every test shares one GitLab project, and several
assertions depend on its state being stable for the duration of a test: the pagination tests
assert exact page contents, the default-sort test asserts relative ordering, and the label filter
tests assert an exact result count. Parallel methods would let one test's writes appear in
another's result set and produce failures unrelated to the API.

**Cleanup is automatic.** `BaseTest` registers every issue it creates and deletes them in
teardown. Cleanup tolerates a 404 — routine for the many tests that delete their own issue as the
behaviour under test — and logs rather than throws on anything else, so a housekeeping problem
can never be mistaken for a product defect.

---

## 8. Allure report

```bash
mvn allure:report    # target/site/allure-maven-plugin
mvn allure:serve     # generate and open
```

Every client call is an Allure `@Step`, so the report shows the request sequence per test.

---

## 9. Continuous integration

`.github/workflows/tests.yml` runs on push and PR to `master` on manual dispatch.

1. JDK 17 with Maven dependency caching.
2. Fails fast with a single annotation if credentials are missing.
3. Runs the suite with secrets injected as environment variables.
4. Generates the Allure report and publishes results to the run summary.
5. Uploads `allure-results` and `surefire-reports` as artifacts.
6. Publishes the Allure report to GitHub Pages — **from `main` only**, so feature branches and
   PRs cannot overwrite the canonical report.

**One-time setup**

1. **Settings → Secrets and variables → Actions → Secrets**: add `GITLAB_API_TOKEN` and
   `GITLAB_API_PROJECT_ID`.
2. **Variables** tab: optionally add `GITLAB_API_BASE_URL` (self-hosted only). It is a variable
   rather than a secret because it is configuration, not a credential, and keeping it visible
   aids debugging.
3. **Settings → Pages → Source = GitHub Actions**.

The workflow uses a single global concurrency group rather than one keyed on branch. Concurrent
runs would interleave writes to the shared project and break the same assertions that rule out
parallel methods. The weekly schedule exists because three of the behaviours documented in §1 are
undocumented quirks — a cron run detects it when GitLab changes one.

---

## 10. Assumptions and trade-offs

- **Live integration tests, no mock.** The point is to characterise the real API's behaviour;
  a mock would encode assumptions rather than test them. Findings in §1 would all have been
  invisible against a stub.
- **State changes use `state_event`.** GitLab transitions state via `state_event=close|reopen`,
  not the `state` field it returns. The client accepts either form and maps it.
- **The test project is public**, so anonymous reads are permitted by design. Unauthenticated
  read tests would therefore be wrong; the equivalent protection is verified on the write path
  (TC312, TC313, TC223), which holds regardless of visibility.
- **404 rather than 403 for invisible resources.** GitLab deliberately avoids disclosing that a
  resource exists, so 404 is the correct expectation in cross-project tests.
- **Sequential execution costs wall-clock time.** Roughly 60 network round-trips run in series.
  Acceptable for a suite this size; a larger one would need project-per-worker isolation rather
  than a shared fixture.
- **Some tests pin observed rather than documented behaviour.** Where the two differ, the test
  description says so explicitly. A test that silently encodes a quirk as if it were the contract
  is worse than no test at all.

---

## 11. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `IllegalStateException: GitLab project id is not configured` | env vars not visible to the run | export them, or set them on the IDE run configuration |
| 401 on every request | auth scheme does not match the token type | set `GITLAB_API_AUTH_SCHEME=private-token` for a `glpat-` token |
| 404 on every request | wrong project id, or token lacks access | use the numeric id; verify with the curl command in §6 |
| Every `IssueDeleteTests` test fails | token holder is not project Owner | deletion requires Owner; see §5 |
| Allure report empty | no results to render | run `mvn clean test` before `allure:report` |
| Label filter tests fail intermittently | stale data from an interrupted run | delete leftover issues in the test project |