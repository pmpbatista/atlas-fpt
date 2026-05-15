package com.atlasfpt.data.backup

import java.util.concurrent.TimeUnit

enum class BackupFrequency(val label: String, val repeatInterval: Long, val repeatUnit: TimeUnit) {
    DAILY("Daily", 1, TimeUnit.DAYS),
    WEEKLY("Weekly", 7, TimeUnit.DAYS),
    MONTHLY("Monthly", 30, TimeUnit.DAYS),
}
