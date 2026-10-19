package ru.mitv.cleaner

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.util.concurrent.TimeUnit

/** One installed app as shown in the list. */
data class AppRow(
    val label: String,
    val pkg: String,
    val isSystem: Boolean,
    val sizeBytes: Long,          // 0 when unknown (no usage-access permission or old Android)
    val lastUsedMs: Long,         // 0 when unknown
    val isRunning: Boolean,
) {
    /** Days since last launch, or null if unknown. */
    fun daysUnused(now: Long = System.currentTimeMillis()): Long? =
        if (lastUsedMs <= 0L) null else TimeUnit.MILLISECONDS.toDays(now - lastUsedMs)
}

data class MemorySnapshot(
    val ramTotal: Long,
    val ramAvail: Long,
    val storageTotal: Long,
    val storageFree: Long,
)

object MemoryReport {

    fun snapshot(context: Context): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val stat = StatFs(Environment.getDataDirectory().path)
        return MemorySnapshot(
            ramTotal = mi.totalMem,
            ramAvail = mi.availMem,
            storageTotal = stat.totalBytes,
            storageFree = stat.availableBytes,
        )
    }

    fun hasUsageAccess(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        // If the permission is missing, the query returns an empty list.
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, now - TimeUnit.DAYS.toMillis(365), now)
            .isNotEmpty()
    }

    /** Builds the list of installed apps with last-used time, size and running flag. */
    fun loadApps(context: Context): List<AppRow> {
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val lastUsed = HashMap<String, Long>()
        (context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager)?.let { usm ->
            val now = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, now - TimeUnit.DAYS.toMillis(365), now)
                .forEach { s -> lastUsed[s.packageName] = maxOf(lastUsed[s.packageName] ?: 0L, s.lastTimeUsed) }
        }

        val running = am.runningAppProcesses.orEmpty()
            .flatMap { it.pkgList.toList() }
            .toSet()

        return pm.getInstalledApplications(PackageManager.GET_META_DATA).map { app ->
            AppRow(
                label = pm.getApplicationLabel(app).toString(),
                pkg = app.packageName,
                isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                sizeBytes = appSize(context, app),
                lastUsedMs = lastUsed[app.packageName] ?: 0L,
                isRunning = app.packageName in running,
            )
        }.sortedWith(compareByDescending<AppRow> { it.sizeBytes }.thenBy { it.label })
    }

    /** App size (APK + data + cache). Needs usage access on Android 8+; returns 0 otherwise. */
    private fun appSize(context: Context, app: ApplicationInfo): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L
        return try {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val uuid = sm.getUuidForPath(Environment.getDataDirectory())
            val stats = ssm.queryStatsForPackage(uuid, app.packageName, android.os.Process.myUserHandle())
            stats.appBytes + stats.dataBytes + stats.cacheBytes
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Asks Android to stop background processes of the given non-system apps.
     * Returns the number of packages we asked to stop. Foreground apps are not affected.
     */
    fun killBackground(context: Context, rows: List<AppRow>): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var count = 0
        rows.filter { it.isRunning && !it.isSystem && it.pkg != context.packageName }.forEach { row ->
            am.killBackgroundProcesses(row.pkg)
            count++
        }
        return count
    }

    fun availRam(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem
    }

    /** Human-readable size in Russian-style units. */
    fun fmt(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val units = arrayOf("Б", "КБ", "МБ", "ГБ")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        return if (i == 0) "${v.toLong()} ${units[i]}" else String.format("%.1f %s", v, units[i])
    }
}
