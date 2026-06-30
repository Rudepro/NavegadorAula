package com.example.navegadoraula

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.navegadoraula.utils.BrowsingHistoryManager

/**
 * HistoryAdapter
 *
 * RecyclerView.Adapter para mostrar la lista del historial de navegación.
 *
 * Cada elemento muestra:
 *  - Título de la página (o URL si no hay título).
 *  - Dominio corto (sin "www.").
 *  - Hora y fecha de visita.
 *  - Botón "×" para eliminar esa entrada.
 *
 * Al tocar una fila, se navega a esa URL (via [onUrlSelected]).
 * Al tocar "×", se elimina la entrada (via [onDeleteEntry]).
 */
class HistoryAdapter(
    private var entries: MutableList<BrowsingHistoryManager.HistoryEntry>,
    private val onUrlSelected: (String) -> Unit,
    private val onDeleteEntry: (Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_history_title)
        val tvDomain: TextView = itemView.findViewById(R.id.tv_history_domain)
        val tvTime: TextView = itemView.findViewById(R.id.tv_history_time)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_entry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val entry = entries[position]

        holder.tvTitle.text = entry.title
        holder.tvDomain.text = entry.shortDomain()
        holder.tvTime.text = entry.formattedTime()

        // Navegar al tocar la fila
        holder.itemView.setOnClickListener {
            onUrlSelected(entry.url)
        }

        // Eliminar al tocar el botón "×"
        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onDeleteEntry(pos)
            }
        }
    }

    override fun getItemCount(): Int = entries.size

    /**
     * Actualiza la lista de entradas y notifica al RecyclerView.
     */
    fun updateEntries(newEntries: List<BrowsingHistoryManager.HistoryEntry>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    /**
     * Elimina el elemento en la posición dada y notifica al RecyclerView.
     */
    fun removeAt(position: Int) {
        if (position >= 0 && position < entries.size) {
            entries.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, entries.size)
        }
    }
}
