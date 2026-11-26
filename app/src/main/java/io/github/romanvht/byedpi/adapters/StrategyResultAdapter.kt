package io.github.romanvht.byedpi.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.StrategyResult
import androidx.core.view.isGone
import androidx.core.view.isVisible
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.formatTestResult

class StrategyResultAdapter(
    private val context: Context,
    private val onApply: (String) -> Unit
) : RecyclerView.Adapter<StrategyResultAdapter.StrategyViewHolder>() {

    private var isTesting = false
    private val strategies = mutableListOf<StrategyResult>()

    class StrategyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commandTextView: TextView = view.findViewById(R.id.commandTextView)
        val progressLayout: LinearLayout = view.findViewById(R.id.progressLayout)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val progressTextView: TextView = view.findViewById(R.id.progressTextView)
        val expandButton: LinearLayout = view.findViewById(R.id.expandButton)
        val expandTextView: TextView = view.findViewById(R.id.expandTextView)
        val expandIcon: ImageView = view.findViewById(R.id.expandIcon)
        val sitesRecyclerView: RecyclerView = view.findViewById(R.id.sitesRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StrategyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_strategy_result, parent, false)
        val holder = StrategyViewHolder(view)

        holder.commandTextView.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                showCommandMenu(strategies[position].command)
            }
        }

        holder.expandButton.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                strategies[position].isExpanded = !strategies[position].isExpanded
                notifyItemChanged(position)
            }
        }

        return holder
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: StrategyViewHolder, position: Int) {
        val strategy = strategies[position]

        holder.commandTextView.text = strategy.command

        if (strategy.totalRequests > 0) {
            holder.progressLayout.visibility = View.VISIBLE
            holder.progressTextView.visibility = View.VISIBLE

            val isProgress = isTesting && !strategy.isCompleted

            if (isProgress) {
                holder.progressBar.isIndeterminate = true
                holder.progressTextView.text = "${strategy.successCount}/${strategy.totalRequests}"
            } else {
                holder.progressBar.isIndeterminate = false
                holder.progressBar.max = strategy.totalRequests
                holder.progressBar.progress = strategy.successCount
                holder.progressTextView.text = context.formatTestResult(strategy.successCount, strategy.totalRequests, strategy.responseTimeMs)
            }
        } else {
            holder.progressLayout.visibility = View.GONE
        }

        if (strategy.siteResults.isNotEmpty()) {
            holder.expandButton.visibility = View.VISIBLE

            if (strategy.isExpanded) {
                holder.expandTextView.text = context.getString(R.string.test_hide_details)
                holder.expandIcon.rotation = 180f
                holder.sitesRecyclerView.visibility = View.VISIBLE

                val siteAdapter = SiteResultAdapter()
                holder.sitesRecyclerView.layoutManager = LinearLayoutManager(context)
                holder.sitesRecyclerView.adapter = siteAdapter
                siteAdapter.updateSites(strategy.siteResults)
            } else {
                holder.expandTextView.text = context.getString(R.string.test_show_details)
                holder.expandIcon.rotation = 0f
                holder.sitesRecyclerView.visibility = View.GONE
            }
        } else {
            holder.expandButton.visibility = View.GONE
            holder.sitesRecyclerView.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: StrategyViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val strategy = strategies[position]

            if (strategy.totalRequests > 0) {
                holder.progressLayout.visibility = View.VISIBLE
                holder.progressTextView.visibility = View.VISIBLE

                val isProgress = isTesting && !strategy.isCompleted

                if (isProgress) {
                    holder.progressBar.isIndeterminate = true
                    holder.progressTextView.text = "${strategy.successCount}/${strategy.totalRequests}"
                } else {
                    holder.progressBar.isIndeterminate = false
                    holder.progressBar.max = strategy.totalRequests
                    holder.progressBar.progress = strategy.successCount
                    holder.progressTextView.text = context.formatTestResult(strategy.successCount, strategy.totalRequests, strategy.responseTimeMs)
                }
            }

            if (strategy.siteResults.isNotEmpty() && holder.expandButton.isGone) {
                holder.expandButton.visibility = View.VISIBLE
            }

            if (strategy.isExpanded && holder.sitesRecyclerView.isVisible) {
                val adapter = holder.sitesRecyclerView.adapter
                if (adapter is SiteResultAdapter) {
                    adapter.updateSites(strategy.siteResults)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return strategies.size
    }

    fun updateStrategy(strategy: StrategyResult) {
        val position = strategies.indexOfFirst { it === strategy }
        if (position != -1) notifyItemChanged(position, "progress")
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setTestingState(testing: Boolean) {
        if (isTesting == testing) return
        isTesting = testing
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateStrategies(newStrategies: List<StrategyResult>) {
        strategies.clear()

        strategies.addAll(newStrategies.sortedWith(StrategyResult.ORDER))
        notifyDataSetChanged()
    }

    private fun showCommandMenu(command: String) {
        val menuItems = arrayOf(
            context.getString(R.string.cmd_history_apply),
            context.getString(R.string.cmd_history_copy)
        )

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.cmd_history_menu))
            .setItems(menuItems) { _, which ->
                when (which) {
                    0 -> onApply(command)
                    1 -> ClipboardUtils.copy(context, command, "command")
                }
            }
            .show()
    }
}
