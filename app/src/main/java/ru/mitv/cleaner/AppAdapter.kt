package ru.mitv.cleaner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val rows: List<AppRow>,
    private val onClick: (AppRow) -> Unit,
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val pkg: TextView = view.findViewById(R.id.pkg)
        val size: TextView = view.findViewById(R.id.size)
        val tags: TextView = view.findViewById(R.id.tags)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ctx = holder.itemView.context
        val row = rows[position]
        holder.name.text = row.label
        holder.pkg.text = row.pkg
        holder.size.text = if (row.sizeBytes > 0) MemoryReport.fmt(row.sizeBytes) else ctx.getString(R.string.size_unknown)

        val parts = mutableListOf<String>()
        if (row.isSystem) parts += ctx.getString(R.string.system_app)
        if (row.isRunning && !row.isSystem) parts += ctx.getString(R.string.running_bg)
        val days = row.daysUnused()
        if (days == null) {
            // no usage data
        } else if (days >= 30) {
            parts += ctx.getString(R.string.days_ago, days)
        }
        holder.tags.text = parts.joinToString(" · ")

        holder.itemView.setOnClickListener { onClick(row) }
    }
}
