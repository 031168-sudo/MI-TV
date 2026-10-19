package ru.mitv.cleaner

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    private lateinit var summary: TextView
    private lateinit var status: TextView
    private lateinit var list: RecyclerView
    private var rows: List<AppRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        summary = findViewById(R.id.summary)
        status = findViewById(R.id.status)
        list = findViewById(R.id.list)
        list.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refresh() }
        findViewById<Button>(R.id.btnClean).setOnClickListener { cleanBackground() }
        findViewById<Button>(R.id.btnUsageAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh(message: String? = null) {
        Thread {
            val mem = MemoryReport.snapshot(this)
            val apps = MemoryReport.loadApps(this)
            val usage = MemoryReport.hasUsageAccess(this)
            runOnUiThread {
                rows = apps
                summary.text = getString(
                    R.string.memory_summary,
                    MemoryReport.fmt(mem.ramAvail), MemoryReport.fmt(mem.ramTotal),
                    MemoryReport.fmt(mem.storageFree), MemoryReport.fmt(mem.storageTotal),
                )
                status.text = message ?: if (usage) "" else getString(R.string.no_usage_access)
                list.adapter = AppAdapter(rows) { row -> openAppSettings(row.pkg) }
            }
        }.start()
    }

    private fun cleanBackground() {
        val before = MemoryReport.availRam(this)
        Thread {
            val count = MemoryReport.killBackground(this, rows)
            Thread.sleep(1500) // give the system time to free memory
            val after = MemoryReport.availRam(this)
            val msg = getString(
                R.string.cleaned_result,
                count,
                MemoryReport.fmt(before),
                MemoryReport.fmt(after),
            )
            runOnUiThread { refresh(msg) }
        }.start()
    }

    private fun openAppSettings(pkg: String) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        )
    }
}
