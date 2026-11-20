package io.github.romanvht.byedpi.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.SiteResult

class SiteResultAdapter : RecyclerView.Adapter<SiteResultAdapter.SiteViewHolder>() {

    private val sites = mutableListOf<SiteResult>()

    class SiteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val statusDot: View = view.findViewById(R.id.siteStatusDot)
        val siteTextView: TextView = view.findViewById(R.id.siteTextView)
        val resultTextView: TextView = view.findViewById(R.id.siteResultTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_site_result, parent, false)
        return SiteViewHolder(view)
    }

    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
        val site = sites[position]
        val context = holder.itemView.context
        holder.siteTextView.text = site.site
        holder.resultTextView.text = context.getString(R.string.test_result_count, site.successCount, site.totalCount)

        val color = when {
            site.totalCount > 0 && site.successCount >= site.totalCount -> ContextCompat.getColor(context, R.color.md_success)
            site.successCount > 0 -> ContextCompat.getColor(context, R.color.md_warning)
            else -> MaterialColors.getColor(holder.statusDot, androidx.appcompat.R.attr.colorError)
        }
        holder.statusDot.backgroundTintList = ColorStateList.valueOf(color)
    }

    override fun getItemCount(): Int {
        return sites.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateSites(newSites: List<SiteResult>) {
        sites.clear()
        sites.addAll(newSites)
        notifyDataSetChanged()
    }
}
