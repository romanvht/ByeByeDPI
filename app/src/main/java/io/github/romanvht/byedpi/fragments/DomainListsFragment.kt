package io.github.romanvht.byedpi.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.DomainListAdapter
import io.github.romanvht.byedpi.data.DomainList
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.DomainListUtils

class DomainListsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DomainListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.domain_lists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_domains, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_reset_lists -> {
                        confirmReset()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        DomainListUtils.syncLists(requireContext())
        recyclerView = view.findViewById(R.id.domain_lists_recycler)

        view.findViewById<View>(R.id.add_new_list_button).setOnClickListener {
            showListDialog(null)
        }

        adapter = DomainListAdapter(
            onStateChanged = { domainList ->
                DomainListUtils.toggleListActive(requireContext(), domainList.id)
                refreshLists()
            },
            onEdit = { domainList ->
                showListDialog(domainList)
            },
            onDelete = { domainList ->
                DomainListUtils.deleteList(requireContext(), domainList.id)
                refreshLists()
            },
            onCopy = { domainList ->
                copyDomainsToClipboard(domainList)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        refreshLists()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.domain_lists)
    }

    private fun refreshLists() {
        val lists = DomainListUtils.getLists(requireContext())
        adapter.submitList(lists.sortedBy { it.name.lowercase() })
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.domain_list_reset)
            .setMessage(R.string.domain_list_reset_confirm)
            .setPositiveButton(R.string.action_reset) { _, _ ->
                DomainListUtils.resetLists(requireContext())
                Toast.makeText(requireContext(), R.string.domain_list_reset_done, Toast.LENGTH_SHORT).show()
                refreshLists()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun copyDomainsToClipboard(domainList: DomainList) {
        val domainsText = domainList.domains.joinToString("\n")
        ClipboardUtils.copy(requireContext(), domainsText, domainList.name)
    }

    /** Dialog to create a new list ([existing] == null) or to edit an existing one. */
    private fun showListDialog(existing: DomainList?) {
        val context = requireContext()
        val padding = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }

        val nameInput = EditText(context).apply {
            hint = getString(R.string.domain_list_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            setText(existing?.name)
        }

        val domainsInput = EditText(context).apply {
            hint = getString(R.string.domain_list_domains_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI
            minLines = 5
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            textDirection = View.TEXT_DIRECTION_LTR
            setText(existing?.domains?.joinToString("\n"))
        }

        layout.addView(nameInput)
        layout.addView(domainsInput)

        MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.domain_list_add_new else R.string.domain_list_edit)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                val domains = domainsInput.text.toString().lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.domain_list_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (domains.isEmpty()) {
                    Toast.makeText(context, R.string.domain_list_domains_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (existing == null) {
                    if (DomainListUtils.addList(context, name, domains)) {
                        Toast.makeText(context, R.string.domain_list_added, Toast.LENGTH_SHORT).show()
                        refreshLists()
                    } else {
                        Toast.makeText(context, R.string.domain_list_already_exists, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (DomainListUtils.updateList(context, existing.id, name, domains)) {
                        Toast.makeText(context, R.string.domain_list_updated, Toast.LENGTH_SHORT).show()
                        refreshLists()
                    } else {
                        Toast.makeText(context, R.string.domain_list_update_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
