package io.github.romanvht.byedpi.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.DomainList

class DomainListAdapter(
    private val onStateChanged: (DomainList) -> Unit,
    private val onEdit: (DomainList) -> Unit,
    private val onDelete: (DomainList) -> Unit,
    private val onCopy: (DomainList) -> Unit
) : ListAdapter<DomainList, DomainListAdapter.DomainListViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_domain_list, parent, false)
        val holder = DomainListViewHolder(view)

        holder.checkbox.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onStateChanged(getItem(position))
            }
        }

        holder.contentLayout.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                holder.showActionDialog(getItem(position), onEdit, onCopy, onDelete)
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: DomainListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DomainListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.list_name)
        private val sizeText: TextView = itemView.findViewById(R.id.list_size)
        private val domainsText: TextView = itemView.findViewById(R.id.list_count)
        val checkbox: CompoundButton = itemView.findViewById(R.id.list_checkbox)
        val contentLayout: LinearLayout = itemView.findViewById(R.id.list_content)

        fun bind(domainList: DomainList) {
            val resources = itemView.resources
            nameText.text = domainList.name
            sizeText.text = resources.getQuantityString(R.plurals.domain_count, domainList.domains.size, domainList.domains.size)

            val preview = domainList.domains.take(3).joinToString("\n")
            domainsText.text = if (domainList.domains.size > 3) "$preview\n…" else preview

            checkbox.isChecked = domainList.isActive
            checkbox.contentDescription = resources.getString(R.string.domain_list_use_in_test, domainList.name)
        }

        fun showActionDialog(
            domainList: DomainList,
            onEdit: (DomainList) -> Unit,
            onCopy: (DomainList) -> Unit,
            onDelete: (DomainList) -> Unit
        ) {
            val context = itemView.context
            val options = arrayOf(
                context.getString(R.string.action_edit),
                context.getString(R.string.action_copy),
                context.getString(R.string.action_delete)
            )

            MaterialAlertDialogBuilder(context)
                .setTitle(domainList.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onEdit(domainList)
                        1 -> onCopy(domainList)
                        2 -> confirmDelete(domainList, onDelete)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun confirmDelete(domainList: DomainList, onDelete: (DomainList) -> Unit) {
            val context = itemView.context
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.domain_list_delete_confirm, domainList.name))
                .setMessage(if (domainList.isBuiltIn) R.string.domain_list_delete_builtin_hint else R.string.action_cannot_undo)
                .setPositiveButton(R.string.action_delete) { _, _ -> onDelete(domainList) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DomainList>() {
        override fun areItemsTheSame(oldItem: DomainList, newItem: DomainList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DomainList, newItem: DomainList): Boolean {
            return oldItem == newItem
        }
    }
}
