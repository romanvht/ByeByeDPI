package io.github.romanvht.byedpi.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getCmdEnable
import io.github.romanvht.byedpi.utility.getPreferences

class StrategyResultAdapter(
    private val context: Context,
    private val onApply: (String) -> Unit,
    private val onCopy: (String) -> Unit,
) : RecyclerView.Adapter<StrategyResultAdapter.StrategyViewHolder>() {

    private var isTesting = false
    private val strategies = mutableListOf<StrategyResult>()
    private var bestCommand: String? = null

    /** Success rate thresholds for the colors of the result badges. */
    enum class Grade { Good, Partial, Bad }

    companion object {
        fun grade(percentage: Int): Grade = when {
            percentage >= 80 -> Grade.Good
            percentage >= 40 -> Grade.Partial
            else -> Grade.Bad
        }
    }

    class StrategyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val scoreBadge: TextView = view.findViewById(R.id.score_badge)
        val bestBadge: TextView = view.findViewById(R.id.best_badge)
        val commandTextView: TextView = view.findViewById(R.id.commandTextView)
        val progressBar: LinearProgressIndicator = view.findViewById(R.id.progressBar)
        val progressTextView: TextView = view.findViewById(R.id.progressTextView)
        val expandButton: MaterialButton = view.findViewById(R.id.expandButton)
        val copyButton: MaterialButton = view.findViewById(R.id.copyButton)
        val applyButton: MaterialButton = view.findViewById(R.id.applyButton)
        val sitesRecyclerView: RecyclerView = view.findViewById(R.id.sitesRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StrategyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_strategy_result, parent, false)
        val holder = StrategyViewHolder(view)

        holder.sitesRecyclerView.layoutManager = LinearLayoutManager(parent.context)
        holder.sitesRecyclerView.adapter = SiteResultAdapter()

        holder.applyButton.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onApply(strategies[position].command)
        }

        holder.copyButton.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onCopy(strategies[position].command)
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

    override fun onBindViewHolder(holder: StrategyViewHolder, position: Int) {
        val strategy = strategies[position]
        holder.commandTextView.text = strategy.command
        bindProgress(holder, strategy)
        bindDetails(holder, strategy)
    }

    override fun onBindViewHolder(holder: StrategyViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val strategy = strategies[position]
            bindProgress(holder, strategy)
            bindDetails(holder, strategy)
        }
    }

    private fun bindProgress(holder: StrategyViewHolder, strategy: StrategyResult) {
        val running = isTesting && !strategy.isCompleted && strategy.totalRequests > 0

        when {
            strategy.isCompleted -> {
                val percentage = strategy.successPercentage
                val (container, onContainer, indicator) = colors(grade(percentage))
                holder.scoreBadge.text = context.getString(R.string.test_percent, percentage)
                holder.scoreBadge.backgroundTintList = ColorStateList.valueOf(container)
                holder.scoreBadge.setTextColor(onContainer)
                showDeterminate(holder.progressBar, strategy.successCount, strategy.totalRequests)
                holder.progressBar.setIndicatorColor(indicator)
            }

            running -> {
                holder.scoreBadge.text = "…"
                neutralBadge(holder.scoreBadge)
                holder.progressBar.setIndicatorColor(MaterialColors.getColor(holder.progressBar, androidx.appcompat.R.attr.colorPrimary))
                showDeterminate(holder.progressBar, strategy.currentProgress, strategy.totalRequests)
            }

            else -> {
                holder.scoreBadge.text = "—"
                neutralBadge(holder.scoreBadge)
                holder.progressBar.visibility = View.GONE
            }
        }

        holder.progressTextView.text = if (strategy.totalRequests > 0) {
            context.getString(R.string.test_result_count, strategy.successCount, strategy.totalRequests)
        } else {
            ""
        }
        holder.bestBadge.visibility = if (!isTesting && strategy.command == bestCommand) View.VISIBLE else View.GONE

        val prefs = context.getPreferences()
        val inUse = prefs.getCmdEnable() && prefs.getCmdArgs() == strategy.command
        holder.applyButton.isEnabled = !isTesting && !inUse
        holder.applyButton.text = context.getString(if (inUse) R.string.cmd_history_applied else R.string.action_apply)
    }

    private fun bindDetails(holder: StrategyViewHolder, strategy: StrategyResult) {
        holder.expandButton.visibility = if (strategy.siteResults.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        holder.expandButton.setText(if (strategy.isExpanded) R.string.test_hide_details else R.string.test_show_details)
        holder.expandButton.setIconResource(if (strategy.isExpanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down)

        val expanded = strategy.isExpanded && strategy.siteResults.isNotEmpty()
        holder.sitesRecyclerView.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded) (holder.sitesRecyclerView.adapter as SiteResultAdapter).updateSites(strategy.siteResults)
    }

    private fun showDeterminate(progressBar: LinearProgressIndicator, value: Int, max: Int) {
        if (max <= 0) {
            progressBar.visibility = View.GONE
            return
        }
        progressBar.visibility = View.VISIBLE
        progressBar.max = max
        progressBar.setProgressCompat(value.coerceIn(0, max), false)
    }

    private fun neutralBadge(badge: TextView) {
        badge.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(badge, com.google.android.material.R.attr.colorSurfaceContainerHighest)
        )
        badge.setTextColor(MaterialColors.getColor(badge, com.google.android.material.R.attr.colorOnSurfaceVariant))
    }

    /** Container, on-container and indicator colors for a grade. */
    private fun colors(grade: Grade): Triple<Int, Int, Int> = when (grade) {
        Grade.Good -> Triple(
            ContextCompat.getColor(context, R.color.md_success_container),
            ContextCompat.getColor(context, R.color.md_on_success_container),
            ContextCompat.getColor(context, R.color.md_success),
        )
        Grade.Partial -> Triple(
            ContextCompat.getColor(context, R.color.md_warning_container),
            ContextCompat.getColor(context, R.color.md_on_warning_container),
            ContextCompat.getColor(context, R.color.md_warning),
        )
        Grade.Bad -> Triple(
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorErrorContainer, 0),
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnErrorContainer, 0),
            MaterialColors.getColor(context, androidx.appcompat.R.attr.colorError, 0),
        )
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

        val sorted = newStrategies.sortedWith(compareByDescending<StrategyResult> { it.isCompleted }
            .thenByDescending { it.successPercentage }
            .thenByDescending { it.successCount })

        strategies.addAll(sorted)
        bestCommand = sorted.firstOrNull { it.isCompleted && it.successCount > 0 }?.command
        notifyDataSetChanged()
    }
}
