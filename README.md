# ReachFlow 🚀

**ReachFlow** is a professional-grade outreach and lead generation platform designed to automate the discovery of prospects and streamline campaign management. Originally conceived as a tool to help find **Ausbildung (Apprenticeship)** opportunities in Germany, it has evolved into a robust full-stack solution integrating advanced automation workflows.

---

## 🌟 The "Ausbildung Finder" Story
ReachFlow was born out of a real-world need: helping a friend navigate the complex German apprenticeship market. By automating the search process—identifying local businesses via Google Maps, extracting contact information, and managing outreach—ReachFlow transformed a manual, time-consuming task into a streamlined, data-driven operation.

---

## 🛠️ Technical Architecture

ReachFlow follows a modern, decoupled architecture:

- **Frontend**: Angular 20 with Angular Material.
- **Backend**: Spring Boot (Java) providing a RESTful API, with a separate archive datasource for historical/closed campaign data.
- **Automation Engine**: n8n integration for Google Maps scraping and site-level email extraction.
- **Security**: Transparent field-level encryption (AES) for sensitive data (e.g., Gmail App Passwords) using a JPA `AttributeConverter`.
- **Infrastructure**: Fully containerized using Docker and Docker Compose.

### 🔀 How a lead search reaches n8n

Every lead collection — whether it's a broad multi-city search or launching a single search combination — goes through **one backend-mediated job/poll pattern**. The browser never talks to n8n directly.

```mermaid
flowchart LR
    U["Angular UI\nRun Search / Launch combination"] -->|"POST /api/leads/collect\ncities, keywords, maxResults, webhookUrl"| LC[LeadController]
    LC -->|"returns {jobId, status} immediately"| U
    LC --> LS["LeadService\n@Async collectLeadsAsyncInternal"]
    LS -->|"blocking POST to webhookUrl\n10-min read timeout"| N8N[n8n Webhook]
    N8N --> AP[Apify Google Maps actor]
    AP --> SC["scrape website pages\nimpressum/kontakt/contact + regex"]
    SC -->|"Respond to Webhook\nsame HTTP response"| LS
    LS -->|"createOrSkipLead: dedup + merge\nupdates job progress"| DB[(MySQL)]
    U -->|"poll every 2s until COMPLETED/FAILED"| SP["GET /api/leads/scrape-status/:jobId"]
    SP --> LC
    U -->|"on completion: mark combination LAUNCHED/FAILED"| SCC["POST /api/search-combinations/:id/launch\n(only for the 'Launch' entry point)"]
```

There is **no callback endpoint** — the `@Async` backend thread blocks on the n8n HTTP call itself and waits for the *same response* to contain the scraped results (matching n8n's `Respond to Webhook` node). All entry points converge on this one path:

- **"Run Search"** (multi-city/keyword batch) calls it with the full search set.
- **"Launch"** (a single search combination) calls the exact same endpoint with a one-city, one-keyword payload, polls the same way, and on completion additionally calls `POST /api/search-combinations/{id}/launch` to record `LAUNCHED`/`FAILED` status on that combination row.

**Why one path, not two:** an earlier version had "Launch" post straight from the browser to the n8n webhook, bypassing the backend, so results could be previewed and selectively saved before committing them. Once the scraping/dedup logic (`LeadService.createOrSkipLead` — see below) matured enough to be trusted unconditionally, that manual review step stopped adding value, so the direct-to-n8n call was removed in favor of routing everything through the same job-based endpoint. This also closes a minor security gap: the raw n8n webhook URL is no longer exposed to, or callable directly from, the browser.

**Implementation note:** both entry points live in `lead-list.component.ts`. The polling mechanism was generalized from a single `pollingSubscription` field to a `pollingSubscriptions: Map<string, any>` keyed by `jobId`, via `startPollingForJob(jobId, combinationId)` — passing `combinationId: null` for a plain "Run Search" job, or an actual id for a "Launch", which additionally calls `launchSearchCombination()` with `LAUNCHED`/`FAILED` once that job's poll resolves. This lets a "Run Search" and one or more "Launch" calls run concurrently without interfering with each other. The old direct-webhook code path (`collectFromWebhook()` in `lead.service.ts`, plus the results-preview UI card and its supporting fields/methods in the component and template) was deleted rather than left dormant.

There's one exception, by design: the **Ausbildung Finder page** (`AusbildungFinderComponent`, openable from the sidenav) posts directly from the browser to a separate, hardcoded n8n webhook (`ausbildung.service.ts` → `http://localhost:5678/webhook-test/ausbildung-finder`) — a deliberately simple, standalone demo flow, not part of the main lead-collection pipeline. See the n8n section below for its webhook requirements.

Despite the similar name, this standalone page is unrelated to the "Ausbildung" category/keyword that `CategorySeeder` seeds as base reference data (see the Base Reference Data & Resetting the Environment section below) — it's easy to confuse the two, but they don't share any code or data.

### 🧹 Deduplication & data quality

`LeadService.createOrSkipLead` runs identically no matter which entry point triggered the collection:

- **Three-tier dedup** (`findExistingLead`): matches on website first, then institution name + city, then any already-known email. This lets the same business turn up across multiple search combinations without creating duplicate lead records.
- **Email merge, not overwrite** (`mergeEmailsIntoLead`): newly scraped emails are added to the existing lead rather than replacing its data, and an existing primary email is never silently demoted.
- **Post-hoc invalid-email detection**: a SQL-backed audit view (`GET /api/leads/emails/audit?mode=invalid`) flags emails that fail a regex check or match known scraper artifacts (`.png`/`.jpg` suffixes from image URLs, `@2x` retina-asset naming, double-dash junk, etc.) for manual review — invalid-looking emails are saved first and surfaced for cleanup rather than silently dropped.

### ✉️ Campaign & delivery flow (separate from lead collection)

Once leads exist in the database, campaigns are a fully separate flow:

```mermaid
flowchart LR
    CS[CampaignService] -->|"startCampaign"| CX["CampaignExecutionService\n@Async startCampaignAsync"]
    CX -->|"build + dispatch"| MS[MailService]
    MS -->|"Gmail SMTP\nAES-decrypted app password"| SMTP[Gmail]
    SCHED["CampaignSchedulerService\n@Scheduled every 60s"] -->|"runs due campaigns"| CS
```

`CampaignSchedulerService` polls every 60 seconds for campaigns due to run and hands them to `CampaignService`/`CampaignExecutionService`, which sends via `MailService` over Gmail SMTP using the client's AES-encrypted app password.

---

## 🧠 Design Decisions & Rationale

### ❓ Why n8n for Scraping (Instead of a Custom Java Scraper)
Writing a reliable Google Maps scraper + headless-browser site crawler from scratch in Java is weeks of work (rate limits, DOM drift, captchas, proxy rotation). n8n decouples that brittle automation from the Spring Boot business logic — Apify is pre-integrated as a battle-tested provider for Google Maps results, and workflow changes are drag-and-drop with no backend redeploy.

### ❓ Why a Job ID + Polling Pattern (Instead of a Webhook Callback)
The scrape can legitimately take minutes (many search-string combinations × per-site email extraction). Rather than holding a client HTTP connection open or building a separate callback endpoint, `POST /api/leads/collect` returns a `jobId` immediately, and the actual scrape runs on an `@Async` thread that blocks on n8n's response internally. The frontend polls `GET /api/leads/scrape-status/{jobId}` every 2 seconds to update progress. This trades an extra polling loop for a much simpler integration surface — one webhook URL, one response shape, no public callback route, and one single entry point for every kind of lead collection (see above).

### ❓ Why Java / Spring Boot for the Backend
- **Transactional consistency**: Campaigns, leads, CampaignSends, and audit records must agree — `@Transactional` + JPA makes this safe.
- **`@Async` + `@Scheduled` are first-class**: non-blocking scraping and scheduled campaign dispatch are built-in primitives, used directly in `LeadService`, `CampaignExecutionService`, and `CampaignSchedulerService`.
- **JPA Attribute Converter pattern**: a clean, transparent way to add field-level AES encryption to `Client.appPassword` without touching service code.

### ❓ Why Angular (Not React)
The UI is a dashboard-first experience — search filters, campaign controls, paginated audit tables. Angular Material provides tables, paginators, modals, and steppers out of the box, and strict-typed reactive forms fit the campaign editor without a third-party form library.

### ❓ Why DTOs (Not Raw Entities)
Every endpoint uses a dedicated DTO (`LeadDTO`, `ClientDTO`, `CampaignDTO`, `SearchCombinationDTO`, etc.), avoiding circular-reference JSON issues and keeping the public API shape independent of entity changes.

### ❓ Why AES Encryption Even in Local-Only
Gmail App Passwords are stored via a JPA `AttributeConverter` that AES-encrypts before write and decrypts on read (`AttributeEncryptor`, using an `encryption.key` property, defaulting to a dev key if unset). For a personal project, plain text would technically work — the encryption exists to demonstrate the pattern a real SaaS deployment would use, with zero operational overhead: it just works out of the box, or you can set `encryption.key` for a real deployment.

---

## ✨ Key Features

### 🔍 Intelligent Discovery
- Multi-source scraping via Apify (Google Maps) and a custom email extractor hitting `/impressum`, `/kontakt`, `/contact`, and the homepage.
- One unified collection pipeline (background job + progress polling) for both broad multi-city searches and single search-combination launches.
- A dedicated Ausbildung Finder demo mode for apprenticeship opportunities in Germany.

### 📊 Analytics & Auditing
- Email audit views for invalid/duplicate addresses.
- Search combination tracking (per city × keyword, with LAUNCHED/FAILED status).
- An archive datastore for historical campaigns, sends, and clients (`ArchiveController`, separate `reachflow_archive` schema).

### ✉️ Campaign Management
- Scheduled campaign execution (`CampaignSchedulerService`, every 60s) and selective/manual sends.
- Asynchronous email dispatch via Spring `@Async`.
- Gmail SMTP delivery with AES-encrypted app passwords.

### 🛡️ Security
- Local AES encryption for stored credentials.
- DTO-based API boundary — entities never travel across the wire.

---

## 🌱 Base Reference Data & Resetting the Environment

Two kinds of data live in the database, and it's worth knowing the difference before you run `docker-compose down -v`:

- **Base reference data** — German places (states/cities/districts) and a small set of starter categories/keywords (e.g. "Ausbildung"). This is safe, reusable scaffolding, not user content.
- **Everything else** (leads, clients, campaigns, search combinations) — real work product, wiped on `down -v` by design.

Both kinds of reference data **self-seed automatically and idempotently**, so a fresh start needs no manual setup:

- **Places**: seeded lazily the first time the frontend loads the Leads or Ausbildung Finder page (`POST /api/search-combinations/seed-germany` → `SearchCombinationService.seedGermanyHierarchy()`), using a find-or-create pattern — safe to call repeatedly, never duplicates.
- **Categories & keywords**: seeded once on backend startup via `CategorySeeder` (a `CommandLineRunner`), following the same find-or-create pattern. It creates a small starter set (Ausbildung, IT Services, Hospitality, each with a few keywords) only if they don't already exist — it never touches leads, clients, or anything you've customized.

**Recommended reset flow:**
```bash
docker-compose down -v      # wipes MySQL + n8n data volumes
docker-compose up -d        # backend seeds categories on boot; frontend seeds places on first load
```
No manual data entry needed to get back to a working demo state.

## 🚀 Getting Started

### Prerequisites
- Docker & Docker Compose
- Node.js & npm (for local frontend development)
- Java 21 & Maven (for local backend development)
- **Apify API Key** (required for the Google Maps scraper in the n8n workflow — sign up at [apify.com](https://apify.com/))

### Quick Start
1. Clone the repository:
   ```bash
   git clone https://github.com/Majdabbassi/Ausbildung_n8n.git
   cd Ausbildung_n8n
   ```
2. Start the environment:
   ```bash
   docker-compose up -d
   ```
3. Access the applications:
   - **Frontend**: `http://localhost:8088`
   - **Backend API**: `http://localhost:8082` (proxies to container port 8080)
   - **n8n Workflow**: `http://localhost:5678`
   - **phpMyAdmin**: `http://localhost:5051`

Then continue with the **n8n Workflow Import** section below to wire up the scraper.

---

## 🤖 n8n Workflow — Import & Configuration Guide

The workflow lives in the repo root as `emails_collector-n8n_automation` (exported n8n JSON) and runs as four nodes:

1. **Webhook** — receives `POST { cities, keywords, maxResults }`.
2. **parse apify input** *(Code)* — normalizes `cities`/`keywords` (handles arrays, JSON strings, or objects with `name`/`value`/`label`), builds every `"{keyword} in {city}"` combination as `searchStringsArray`, plus `maxCrawledPlacesPerSearch`.
3. **Run an Actor and get dataset** *(Apify)* — runs the Google Maps Scraper actor (`compass/crawler-google-places`) with those search strings.
4. **scrape website pages** *(Code)* — for each place with a website, fetches `/impressum`, the homepage, `/kontakt`, `/contact`, regex-extracts emails, dedupes, and filters out obvious asset-file false positives (`.png`, `.jpg`, `.css`, `.js`, etc.).
5. **Respond to Webhook** — returns `{ results: [...] }` directly as the HTTP response. **There is no separate callback request** to the backend.

### Step 1 — Import the JSON Workflow
1. Open the n8n UI at `http://localhost:5678`.
2. Click the **☰ hamburger menu** → **Workflows** → **+ New Workflow**.
3. In the top-right, click **"…"** → **Import from File**.
4. Select `emails_collector-n8n_automation` from the project root.
5. Save the imported workflow.

### Step 2 — Configure the Apify Credential
1. Click the **Apify** node in the workflow canvas.
2. Open the **Credentials** dropdown → **Create new credential**.
3. Paste your Apify API token (from `console.apify.com` → Settings → Integrations → Personal API Tokens).
4. Save the credential and select it for the node.

### Step 3 — Get the Webhook URL
1. Click the **Webhook** node (top-left).
2. Copy the **Test / Production webhook URL**.
3. Paste that URL into the frontend's webhook URL field — it's **passed per-request** (via `CollectRequestDTO.webhookUrl` or the launch-combination payload), not read from a backend environment variable. There is no `N8N_WEBHOOK_URL` property in `application.properties`.
4. Verify the method is `POST` and expects a JSON body: `{ "keyword": "Ausbildung Fachinformatiker", "cities": ["Berlin"] }`.

### Step 4 — Test End-to-End
- Click the **Webhook** node → **Listen for test event** in n8n.
- In the frontend → Leads → **Run Search** (or **Launch** a search combination).
- Watch n8n execute each node. When it responds, the backend (Path A) imports leads automatically, or the browser (Path B) shows results for manual bulk-import.

### Backend Endpoints Involved
- `POST /api/leads/collect` — starts an async job, returns `{ jobId, status }` immediately ([LeadController.java](file:///backend/src/main/java/com/majd/reachflow/controller/LeadController.java)). Used by both "Run Search" and "Launch a combination".
- `GET /api/leads/scrape-status/{jobId}` — polled by the frontend every 2s for progress/completion.
- `POST /api/search-combinations/{id}/launch` — records LAUNCHED/FAILED status once a combination's job completes.
- `POST /api/leads/bulk` — still available for manual CSV/bulk imports outside the scraping pipeline.

All async scraping logic lives in [LeadService.java](file:///backend/src/main/java/com/majd/reachflow/service/LeadService.java), which wraps a `RestTemplate` POST to the n8n webhook with a 10-minute socket timeout to accommodate large map-scrape jobs.

---

## 📈 Future Roadmap
- [ ] Visual Email Template Editor
- [ ] Email tracking pixel for Open Rate monitoring
- [ ] AI-driven personalized email generation
- [ ] LinkedIn integration for multi-channel outreach

---

*Developed by Majd - A showcase of full-stack engineering and automation.*
