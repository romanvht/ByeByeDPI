package io.github.dovecoteescapee.byedpi.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.adapters.AppSelectionAdapter
import io.github.dovecoteescapee.byedpi.data.AppInfo
import io.github.dovecoteescapee.byedpi.data.AppsCache
import kotlinx.coroutines.launch

class AppSelectionFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var adapter: AppSelectionAdapter
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.app_selection_layout, container, false)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        recyclerView = view.findViewById(R.id.recyclerView)
        searchView = view.findViewById(R.id.searchView)

        setupRecyclerView()
        setupSearchView()

        searchView.visibility = View.VISIBLE

        loadApps()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView.adapter = null
        searchView.setOnQueryTextListener(null)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })
    }

    private fun loadApps() {
        val selectedApps = prefs.getStringSet("selected_apps", setOf()) ?: setOf()

        lifecycleScope.launch {
            val apps = AppsCache.loadApps(requireContext(), selectedApps)
            updateAdapter(apps)
        }
    }

    private fun updateAdapter(apps: List<AppInfo>) {
        val sortedApps = apps.sortedWith(compareBy({ !it.isSelected }, { it.appName.lowercase() }))

        if (::adapter.isInitialized) {
            adapter = AppSelectionAdapter(requireContext(), sortedApps)
            recyclerView.adapter = adapter
        } else {
            adapter = AppSelectionAdapter(requireContext(), sortedApps)
            recyclerView.adapter = adapter
        }
    }
}