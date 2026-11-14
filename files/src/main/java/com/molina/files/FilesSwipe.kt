// molina-patch26c
package com.molina.files

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Geser baris untuk memilih. Baris belum terpilih hanya bisa digeser ke kanan (pilih), baris terpilih
 * hanya ke kiri (batal pilih), jadi arah selalu cocok dengan efeknya. Ambang 64 dp, baris ikut
 * bergeser paling jauh 28 dp (sama dengan penjelajah repo lama). Lemparan cepat tidak memicu.
 */
internal class FilesSwipeCallback(
    private val density: Float,
    private val isSelected: (position: Int) -> Boolean,
    private val onSelect: (position: Int) -> Unit,
    private val onDeselect: (position: Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return 0
        return if (isSelected(pos)) ItemTouchHelper.LEFT else ItemTouchHelper.RIGHT
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        val width = viewHolder.itemView.width.coerceAtLeast(1)
        return (THRESHOLD_DP * density / width).coerceIn(0.05f, 0.9f)
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * VELOCITY_GUARD

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = defaultValue * VELOCITY_GUARD

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return
        if (direction == ItemTouchHelper.RIGHT) onSelect(pos) else onDeselect(pos)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val cap = CAP_DP * density
        super.onChildDraw(c, recyclerView, viewHolder, (dX * DAMPING).coerceIn(-cap, cap), dY, actionState, isCurrentlyActive)
    }

    private companion object {
        const val THRESHOLD_DP = 64f
        const val CAP_DP = 28f
        const val DAMPING = 0.4f
        const val VELOCITY_GUARD = 50f
    }
}
