package com.casureco2.outages.util

object Config {
    const val GITHUB_BRANCH = "main"
    const val SCRAPE_HASHTAG = "casureco2"
    const val SCRAPE_MAX_PAGES = 10
    val SCRAPE_PAGE_DELAY_MS = 2000L..4500L
    const val PARSE_SKIP_THRESHOLD_DAYS = 90
    const val WORKER_SCHEDULE_DAY_OF_WEEK = java.util.Calendar.SUNDAY
    const val WORKER_SCHEDULE_HOUR = 3
    const val WORKER_SCHEDULE_MINUTE = 0
    const val FB_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    const val NOTIFICATION_CHANNEL_ID = "casureco2_outages"
}
