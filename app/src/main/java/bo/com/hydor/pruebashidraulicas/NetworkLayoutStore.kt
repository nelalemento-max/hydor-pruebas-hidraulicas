package bo.com.hydor.pruebashidraulicas

import android.content.Context

object NetworkLayoutStore {
    private const val PREFS = "hydor_network_layout"

    data class NodePoint(val x: Float, val y: Float)

    data class LayoutState(
        val includedSectionIds: Set<Long>,
        val bends: Map<Long, Float>,
        val topologyCodes: Map<Long, String>,
        val saved: Boolean,
        val consolidated: Boolean,
        val consolidatedAt: Long
    )

    fun load(context: Context, projectId: Long, defaultSectionIds: Set<Long>): LayoutState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val includedRaw = p.getString("included_$projectId", null)
        val included = includedRaw?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet() ?: defaultSectionIds

        val bendRaw = p.getString("bends_$projectId", "").orEmpty()
        val bends = bendRaw.split(';').mapNotNull { token ->
            val parts = token.split(':')
            if (parts.size != 2) null else {
                val id = parts[0].toLongOrNull()
                val bend = parts[1].toFloatOrNull()
                if (id != null && bend != null) id to bend else null
            }
        }.toMap()

        val topologyRaw = p.getString("topology_$projectId", "").orEmpty()
        val topology = topologyRaw.split(';').mapNotNull { token ->
            val parts = token.split(':', limit = 2)
            val id = parts.getOrNull(0)?.toLongOrNull()
            val code = parts.getOrNull(1)?.trim().orEmpty()
            if (id != null && code.isNotEmpty()) id to code else null
        }.toMap()

        return LayoutState(
            includedSectionIds = included,
            bends = bends,
            topologyCodes = topology,
            saved = p.getBoolean("saved_$projectId", false),
            consolidated = p.getBoolean("consolidated_$projectId", false),
            consolidatedAt = p.getLong("consolidated_at_$projectId", 0L)
        )
    }

    fun saveLayout(
        context: Context,
        projectId: Long,
        included: Set<Long>,
        bends: Map<Long, Float>,
        topologyCodes: Map<Long, String>
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("included_$projectId", included.sorted().joinToString(","))
            .putString("bends_$projectId", bends.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" })
            .putString("topology_$projectId", topologyCodes.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value.trim().lowercase()}" })
            .putBoolean("saved_$projectId", true)
            .apply()
    }

    fun setConsolidated(context: Context, projectId: Long, value: Boolean) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = p.edit().putBoolean("consolidated_$projectId", value)
        if (value) {
            if (p.getLong("consolidated_at_$projectId", 0L) == 0L) {
                editor.putLong("consolidated_at_$projectId", System.currentTimeMillis())
            }
        } else {
            editor.remove("consolidated_at_$projectId")
        }
        editor.apply()
    }

    fun isConsolidated(context: Context, projectId: Long): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("consolidated_$projectId", false)

    fun consolidatedAt(context: Context, projectId: Long): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("consolidated_at_$projectId", 0L)
}
