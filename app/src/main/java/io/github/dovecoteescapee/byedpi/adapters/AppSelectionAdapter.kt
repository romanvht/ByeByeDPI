package io.github.dovecoteescapee.byedpi.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.AppInfo
import io.github.dovecoteescapee.byedpi.data.AppsCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppSelectionAdapter(
    context: Context,
    allApps: List<AppInfo>
) : RecyclerView.Adapter<AppSelectionAdapter.ViewHolder>(), Filterable {

    private val context = context.applicationContext
    private val originalApps: List<AppInfo> = allApps
    private val filteredApps: MutableList<AppInfo> = allApps.toMutableList()
    private val iconCache = HashMap<String, Drawable?>()

    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val appCheckBox: CheckBox = view.findViewById(R.id.appCheckBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_selection_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = filteredApps[position]
        holder.appName.text = app.appName
        holder.appCheckBox.isChecked = app.isSelected

        loadAppIcon(holder, app)

        holder.itemView.setOnClickListener {
            app.isSelected = !app.isSelected
            holder.appCheckBox.isChecked = app.isSelected
            updateSelectedApps()
        }
    }

    private fun loadAppIcon(holder: ViewHolder, app: AppInfo) {
        if (iconCache.containsKey(app.packageName)) {
            iconCache[app.packageName]?.let {
                holder.appIcon.setImageDrawable(it)
            }
            return
        }

        holder.appIcon.setImageDrawable(context.packageManager.defaultActivityIcon)

        app.icon?.let {
            holder.appIcon.setImageDrawable(it)
            iconCache[app.packageName] = it
            return
        }

        holder.itemView.tag = app.packageName
        adapterScope.launch {
            val icon = AppsCache.loadAppIcon(context, app.packageName)
            iconCache[app.packageName] = icon

            launch(Dispatchers.Main) {
                @SuppressLint("RecyclerView")
                if (holder.itemView.tag == app.packageName) {
                    holder.appIcon.setImageDrawable(icon)
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.itemView.tag = null
    }

    override fun getItemCount(): Int {
        return filteredApps.size
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase().orEmpty()
                var filteredList = originalApps

                if (query.isNotEmpty()) {
                    filteredList = originalApps.filter {
                        it.appName.lowercase().contains(query)
                    }
                }

                return FilterResults().apply {
                    values = filteredList
                }
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                val newList = (results.values as List<*>).filterIsInstance<AppInfo>()
                filteredApps.clear()
                filteredApps.addAll(newList)
                notifyDataSetChanged()
            }
        }
    }

    private fun getAllSelectedPackages(): Set<String> {
        return originalApps.filter { it.isSelected }.map { it.packageName }.toSet()
    }

    private fun updateSelectedApps() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val selectedApps = getAllSelectedPackages()
        prefs.edit { putStringSet("selected_apps", selectedApps) }
    }
}