# CASURECO 2 Outage Calendar

An Android app that scrapes CASURECO 2 power outage advisories from Facebook, parses them using an LLM, and publishes per-barangay iCalendar feeds to a GitHub repository served via GitHub Pages.

## Architecture

```
Android App (always-on tablet)
│
├── WorkManager — weekly trigger (Sunday 3:00 AM PHT)
│   ├── ScrapingWorker → Facebook (mbasic.facebook.com)
│   ├── ParserWorker   → OpenCode Zen API (big-pickle)
│   ├── IcsWorker      → biweekly (ICS generation)
│   └── GitHubSyncWorker → GitHub REST API
│
└── Jetpack Compose UI — internal dashboard

GitHub Repository
├── ics/          → per-barangay .ics files
└── docs/         → GitHub Pages static site
```

## Setup

1. Clone this repository
2. Open in Android Studio
3. Build and install on an always-on Android tablet
4. Configure API keys in the app Settings:
   - **OpenCode API Key** — from [opencode.ai](https://opencode.ai)
   - **GitHub Token** — Personal Access Token with `repo` scope
   - **GitHub Owner / Repo** — target repository details
5. Log in to Facebook via the in-app WebView when prompted
6. The app will automatically run every Sunday at 3:00 AM PHT

## GitHub Pages

Enable GitHub Pages in your repository settings with source set to the `/docs` folder on the `main` branch. Residents can then visit `https://your-username.github.io/casureco2-outages/` to find and subscribe to their barangay's calendar.

## Tech Stack

- Kotlin + Android Jetpack Compose
- WorkManager for background jobs
- Room for local persistence
- OkHttp + Jsoup for scraping
- OpenCode Zen API for NLP parsing
- biweekly for ICS generation
- GitHub REST API for publishing

## License

MIT
