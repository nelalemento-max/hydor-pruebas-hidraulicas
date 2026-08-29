package bo.com.hydor.pruebashidraulicas

import android.content.Context

object NetworkLayoutStore {
    private const val PREFS = "hydor_network_layout"

    data class LayoutState(
        val includedSectionIds: Set<Long>,
        val bends: Map<Long, Float>,
        val saved: Boolean,
        val consolidated: Boolean
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
        return LayoutState(
            includedSectionIds = included,
            bends = bends,
            saved = p.getBoolean("saved_$projectId", false),
            consolidated = p.getBoolean("consolidated_$projectId", false)
        )
    }

    fun saveLayout(context: Context, projectId: Long, included: Set<Long>, bends: Map<Long, Float>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("included_$projectId", included.sorted().joinToString(","))
            .putString("bends_$projectId", bends.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" })
            .putBoolean("saved_$projectId", true)
            .apply()
    }

    fun setConsolidated(context: Context, projectId: Long, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("consolidated_$projectId", value)
            .apply()
    }

    fun isConsolidated(context: Context, projectId: Long): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("consolidated_$projectId", false)
}
