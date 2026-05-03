# CASURECO 2 Outage Calendar — Project Specification

**Version:** 1.0  
**Last updated:** 2026-05-03  
**Platform:** Android (Kotlin)

---

## 1. Overview

An Android app that runs on an always-on tablet, scrapes CASURECO 2 power outage
advisories from Facebook weekly, parses them into structured data using an LLM, 
generates per-barangay iCalendar (.ics) files, and publishes them to a GitHub
repository served as a static website via GitHub Pages. Residents subscribe to
their barangay's calendar feed directly in Google Calendar, Apple Calendar, or
any CalDAV-compatible client.

---

## 2. System Architecture

```
Android App (always-on tablet)
│
├── WorkManager — weekly trigger (Sunday 3:00 AM)
│   │
│   ├── 1. ScrapingWorker
│   │       OkHttp → mbasic.facebook.com/hashtag/casureco2
│   │       Jsoup  → parse raw HTML into RawPost list
│   │
│   ├── 2. ParserWorker
│   │       OkHttp → opencode.ai/zen/v1/chat/completions
│   │       Model  → opencode/big-pickle
│   │       Input  → RawPost.text
│   │       Output → ParsedOutage (structured JSON)
│   │
│   ├── 3. IcsWorker
│   │       biweekly (Java) → build one .ics per barangay
│   │       Room DB         → persist outage records, track sync state
│   │
│   └── 4. GitHubSyncWorker
│           GitHub REST API → PUT /repos/.../contents/ics/{barangay}.ics
│                          → PUT /repos/.../contents/docs/barangays.json
│
└── UI (Jetpack Compose) — status dashboard only, not user-facing
        Shows last run time, post count, sync status, error log

GitHub Repository
├── ics/
│   ├── {barangay-slug}.ics     (one per barangay)
│   └── all.ics                 (union feed)
└── docs/                       (GitHub Pages root)
    ├── index.html
    ├── barangays.json
    └── assets/
```

---

## 3. Data Models

### 3.1 RawPost
Produced by ScrapingWorker. Stored in Room.

```
id          TEXT PRIMARY KEY   -- Facebook post permalink URL
author      TEXT               -- Page name (e.g. "CASURECO 2")
text        TEXT               -- Full post body
timestamp   TEXT               -- Raw timestamp string from mbasic
scraped_at  TEXT               -- ISO-8601 UTC
parsed      INTEGER DEFAULT 0  -- 0 = pending, 1 = done, 2 = failed
```

### 3.2 ParsedOutage
Produced by ParserWorker. Stored in Room.

```
id            TEXT PRIMARY KEY   -- raw_post_id (FK → RawPost.id)
barangays     TEXT               -- JSON array of barangay names
date          TEXT               -- ISO date: YYYY-MM-DD
time_start    TEXT               -- HH:MM (24h), nullable
time_end      TEXT               -- HH:MM (24h), nullable
reason        TEXT               -- e.g. "Scheduled maintenance"
scope         TEXT               -- "Whole" | "Partial"
raw_post_id   TEXT               -- FK → RawPost.id
synced        INTEGER DEFAULT 0  -- 0 = pending, 1 = pushed to GitHub
```

### 3.3 Barangay
Reference table. Pre-seeded from known CASURECO 2 service area.

```
id            INTEGER PRIMARY KEY AUTOINCREMENT
name          TEXT UNIQUE     -- canonical display name
slug          TEXT UNIQUE     -- kebab-case, used in filename and URL
municipality  TEXT            -- for grouping in the static site
```

---

## 4. Component Specifications

### 4.1 ScrapingWorker

**Trigger:** Chained after WorkManager schedule. Re-runnable (idempotent).

**Behaviour:**
- GETs `https://mbasic.facebook.com/hashtag/casureco2`
- Paginates up to `SCRAPE_MAX_PAGES` (default: 10)
- Follows `next_url` from "See More" / pagination links
- Extracts posts using Jsoup; skips posts whose permalink already exists in Room
- Inserts new `RawPost` records with `parsed = 0`
- Random delay between pages: 2–4 seconds
- User-Agent: Android Chrome mobile string (see §7)

**Failure handling:**
- On HTTP error or timeout: retry up to 3×, exponential backoff
- On "login" redirect detected in response body: emit `SCRAPER_SESSION_EXPIRED`
  notification and abort chain

**Outputs:** N new `RawPost` rows in Room with `parsed = 0`

---

### 4.2 ParserWorker

**Trigger:** Chained after ScrapingWorker succeeds.

**API:** OpenCode Zen  
- Endpoint: `https://opencode.ai/zen/v1/chat/completions`  
- Model: `opencode/big-pickle`  
- Auth: Bearer token from `OPENCODE_API_KEY` (stored in EncryptedSharedPreferences)

**System prompt:**
```
You are a structured data extractor for power outage advisories in Camarines Sur, Philippines.
Given a raw Facebook post from CASURECO 2 (Camarines Sur II Electric Cooperative), extract the
following fields and respond ONLY with a valid JSON object. No explanation, no markdown.

Fields:
- barangays: array of strings — affected barangay names, normalized to title case.
             If "all barangays" or similar is mentioned, return ["ALL"].
             If a municipality is affected wholesale, list its barangays individually if known,
             otherwise return the municipality name suffixed with " (all)".
- date: ISO date string (YYYY-MM-DD). Infer year from context if missing (current year).
- time_start: "HH:MM" in 24h format, or null if not specified.
- time_end:   "HH:MM" in 24h format, or null if not specified.
- reason: short string describing the cause, e.g. "Scheduled maintenance", "Line repair".
- scope: "Whole" if the entire area is affected, "Partial" otherwise.

If the post is not an outage advisory, return: {"skip": true}
```

**Behaviour:**
- Processes only `RawPost` rows where `parsed = 0`
- Sends one API call per post (posts are short; batching not needed)
- Parses JSON response; inserts `ParsedOutage` rows
- Marks `RawPost.parsed = 1` on success, `2` on failure
- If `skip: true` returned, marks `RawPost.parsed = 1` and creates no outage record
- On API error or malformed JSON: marks `parsed = 2`, logs error, continues to next post

**Outputs:** N new `ParsedOutage` rows in Room with `synced = 0`

---

### 4.3 IcsWorker

**Trigger:** Chained after ParserWorker succeeds.

**Library:** `net.sf.biweekly:biweekly:0.6.8` (Android-compatible, pure Java)

**Behaviour:**
- Queries all `ParsedOutage` rows joined with `RawPost`
- Groups by barangay slug
- For each barangay, builds a `VCalendar` from scratch (full rebuild, not diff):
  - `PRODID`: `-//CASURECO2 Outage Tracker//EN`
  - `VERSION`: `2.0`
  - `X-WR-CALNAME`: `{Barangay Name} — CASURECO 2 Outages`
  - `X-WR-TIMEZONE`: `Asia/Manila`
  - One `VEVENT` per outage:
    - `UID`: `{raw_post_id}@casureco2.github.io`
    - `SUMMARY`: `Power Interruption — {scope}`
    - `DESCRIPTION`: `{reason}\n\nSource: {raw_post_url}`
    - `DTSTART`: date + time_start (or all-day if time_start null)
    - `DTEND`: date + time_end (or next day if all-day)
    - `LOCATION`: `{barangay}, Camarines Sur`
    - `STATUS`: `CONFIRMED`
    - `LAST-MODIFIED`: now
- Also builds `all.ics` as the union of all barangay events
- Serialises each calendar to a UTF-8 string
- Stores in memory / temp file, passes to GitHubSyncWorker

**Outputs:** Map of `barangay-slug → ics_string` including `"all"` key

---

### 4.4 GitHubSyncWorker

**Trigger:** Chained after IcsWorker succeeds.

**Auth:** GitHub Personal Access Token (PAT) with `repo` scope,
stored in EncryptedSharedPreferences under key `GITHUB_TOKEN`.

**Config (hardcoded or in SharedPreferences):**
```
GITHUB_OWNER = "your-username"
GITHUB_REPO  = "casureco2-outages"
GITHUB_BRANCH = "main"
```

**Behaviour:**
- For each `(slug, ics_string)` pair:
  - GET `https://api.github.com/repos/{owner}/{repo}/contents/ics/{slug}.ics`
    to retrieve current file SHA (required for updates; null if new file)
  - PUT same URL with body:
    ```json
    {
      "message": "chore: update {slug}.ics [{date}]",
      "content": "<base64(ics_string)>",
      "sha": "<current_sha or omit if new>",
      "branch": "main"
    }
    ```
- Regenerates `docs/barangays.json` (see §5.2) and PUTs it the same way
- Marks all `ParsedOutage.synced = 1` on success
- On 409 Conflict (SHA mismatch due to concurrent update): fetch new SHA and retry once
- On any other error: log, do not mark synced (will retry next run)

**Rate limiting:** GitHub API allows 5000 requests/hour for authenticated users.
At ~50 barangays this is ~100 requests per run — well within limits.

---

## 5. GitHub Repository

### 5.1 Structure

```
casureco2-outages/
├── ics/
│   ├── all.ics
│   ├── del-rosario.ics
│   ├── ocampo.ics
│   ├── pili.ics
│   └── ...
├── docs/                    ← GitHub Pages root
│   ├── index.html
│   ├── barangays.json
│   └── assets/
│       ├── style.css
│       └── logo.svg
└── README.md
```

### 5.2 barangays.json

Generated and pushed by GitHubSyncWorker after each successful sync.

```json
{
  "generated_at": "2026-05-03T03:00:00Z",
  "barangays": [
    {
      "name": "Del Rosario",
      "slug": "del-rosario",
      "municipality": "Pili",
      "ics_url": "https://raw.githubusercontent.com/{owner}/{repo}/main/ics/del-rosario.ics",
      "webcal_url": "webcal://raw.githubusercontent.com/{owner}/{repo}/main/ics/del-rosario.ics"
    }
  ]
}
```

---

## 6. Static Site (GitHub Pages)

**Source:** `docs/` directory in the repo, served at  
`https://{owner}.github.io/casureco2-outages/`

**Setup:** Enable GitHub Pages in repo Settings → Pages → Source: `docs/` branch `main`.

### 6.1 index.html behaviour

- On load: fetch `barangays.json` (relative path)
- Render a searchable list of barangays grouped by municipality
- Each barangay card shows:
  - Name
  - "Subscribe" button → opens `webcal://...` URL
  - "Copy link" button → copies the `ics_url` to clipboard (for manual import)
- Footer shows `generated_at` timestamp from the JSON
- No build step required; plain HTML + vanilla JS + one CSS file

### 6.2 Subscribe flow (end user)

```
User visits site
  → taps "Subscribe" on their barangay
  → webcal:// URL opens Google Calendar / Apple Calendar
  → calendar app prompts "Subscribe to this calendar?"
  → user confirms
  → events appear; calendar auto-refreshes per the app's poll interval
```

---

## 7. Configuration Reference

All sensitive values stored in Android `EncryptedSharedPreferences`.  
Non-sensitive defaults hardcoded as constants in a `Config.kt` object.

| Key | Type | Storage | Description |
|---|---|---|---|
| `OPENCODE_API_KEY` | String | EncryptedSharedPrefs | OpenCode Zen API key |
| `GITHUB_TOKEN` | String | EncryptedSharedPrefs | GitHub PAT (repo scope) |
| `GITHUB_OWNER` | String | SharedPrefs | GitHub username |
| `GITHUB_REPO` | String | SharedPrefs | Repository name |
| `GITHUB_BRANCH` | String | Constant | `"main"` |
| `SCRAPE_HASHTAG` | String | Constant | `"casureco2"` |
| `SCRAPE_MAX_PAGES` | Int | Constant | `10` |
| `SCRAPE_PAGE_DELAY_MS` | LongRange | Constant | `2000..4500` |
| `PARSE_SKIP_THRESHOLD` | Int | Constant | Skip posts older than 90 days |
| `WORKER_SCHEDULE` | Cron-equivalent | Constant | Weekly, Sunday 03:00 PHT |
| `FB_USER_AGENT` | String | Constant | Android Chrome UA string |
| `FB_COOKIES_FILE` | — | Not used | Session managed via cookie jar in OkHttp |

**FB_USER_AGENT:**
```
Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 
(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36
```

---

## 8. WorkManager Chain

```kotlin
// Runs weekly; each worker is chained so failure stops the chain.

val scrape  = OneTimeWorkRequestBuilder<ScrapingWorker>().build()
val parse   = OneTimeWorkRequestBuilder<ParserWorker>().build()
val ics     = OneTimeWorkRequestBuilder<IcsWorker>().build()
val sync    = OneTimeWorkRequestBuilder<GitHubSyncWorker>().build()

WorkManager.getInstance(context)
    .beginWith(scrape)
    .then(parse)
    .then(ics)
    .then(sync)
    .enqueue()

// Scheduled trigger:
val weekly = PeriodicWorkRequestBuilder<PipelineTriggerWorker>(7, TimeUnit.DAYS)
    .setInitialDelay(nextSundayAt3amPHT(), TimeUnit.MILLISECONDS)
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build())
    .build()

WorkManager.getInstance(context)
    .enqueueUniquePeriodicWork("weekly_scrape", KEEP, weekly)
```

---

## 9. Android App UI

Minimal internal dashboard — not user-facing.

**Screens:**
1. **Dashboard** (home)
   - Last run: timestamp + status badge (Success / Failed / Running)
   - Posts scraped this run / total in DB
   - Outages parsed / skipped / failed
   - Barangays synced to GitHub
   - "Run now" button (manual trigger)
   - "View log" button

2. **Settings**
   - OpenCode API key (masked input)
   - GitHub token (masked input)
   - GitHub owner / repo name
   - "Clear database" (danger zone)

3. **Log viewer**
   - Scrollable list of timestamped log lines from last run
   - Error lines highlighted in red

---

## 10. Notification Spec

| ID | Trigger | Title | Body |
|---|---|---|---|
| `NOTIF_RUNNING` | Pipeline starts | "Scraping in progress" | "Fetching CASURECO 2 advisories…" |
| `NOTIF_SUCCESS` | Pipeline completes | "Sync complete" | "{N} outages updated across {M} barangays" |
| `NOTIF_FAILED` | Any worker fails | "Sync failed" | "{WorkerName}: {error message}" |
| `NOTIF_SESSION` | FB session expired | "Action needed" | "Tap to re-login to Facebook" |

---

## 11. Dependencies (Gradle)

```kotlin
// Networking
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// HTML parsing
implementation("org.jsoup:jsoup:1.17.2")

// JSON
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

// ICS generation
implementation("net.sf.biweekly:biweekly:0.6.8")

// Room (local DB)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Secure storage
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// Jetpack Compose (UI)
implementation(platform("androidx.compose:compose-bom:2024.04.01"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:1.9.0")
```

---

## 12. Error Handling & Recovery

| Scenario | Behaviour |
|---|---|
| Facebook returns login page | Abort pipeline, emit `NOTIF_SESSION`, do not retry until session refreshed |
| OpenCode API 429 / rate limit | Wait 60s, retry up to 3× |
| OpenCode returns non-JSON | Mark `RawPost.parsed = 2`, continue to next post |
| GitHub 409 Conflict | Re-fetch SHA, retry PUT once |
| GitHub 401 Unauthorized | Abort sync, emit `NOTIF_FAILED` |
| No network at scheduled time | WorkManager retries when connectivity restored (constraint) |
| Partial sync (some barangays fail) | Succeeding files are committed; failed ones retry next run |

---

## 13. Facebook Session Management

mbasic.facebook.com requires a logged-in session. The app manages this via
an OkHttp `CookieJar` backed by EncryptedSharedPreferences.

**Initial login:** Done once via an in-app WebView pointed at
`https://mbasic.facebook.com/login`. The WebView's cookie store is copied
into the app's `CookieJar` after successful login. User taps "Done" to confirm.

**Session expiry:** Detected when scraper response body contains
`"log in"` in the first 1KB. Triggers `NOTIF_SESSION` and aborts.

**Re-login:** User taps the notification → opens in-app WebView login flow again.

---

## 14. Open Questions / Future Considerations

- [ ] Should `barangays.json` include the last known outage date per barangay?
- [ ] Should the static site show a live "next outage" countdown per barangay?
- [ ] Barangay seed list: needs a complete canonical list of CASURECO 2 service
      barangays to validate parser output against.
- [ ] Consider moving GitHub sync to a lightweight VPS if tablet connectivity
      proves unreliable (tablet pushes to VPS via simple HTTP POST instead).
- [ ] Big Pickle data retention policy: acceptable for public advisory data,
      but should be re-evaluated if any PII appears in posts.
- [ ] If Big Pickle exits free beta, evaluate switching to GLM-4.7 Free
      (same provider, also free, similar characteristics).

