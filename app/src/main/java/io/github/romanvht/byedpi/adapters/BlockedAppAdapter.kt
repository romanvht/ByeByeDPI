package io.github.romanvht.byedpi.adapters

import android.content.res.ColorStateList
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.BlockedApp
import io.github.romanvht.byedpi.utility.AppAction
import io.github.romanvht.byedpi.utility.AppActions
import io.github.romanvht.byedpi.utility.AppCatalogUtils
import io.github.romanvht.byedpi.utility.AppDownloads
import java.util.Locale

class BlockedAppAdapter(private val listener: Listener) :
    ListAdapter<BlockedAppAdapter.Row, RecyclerView.ViewHolder>(Diff) {

    interface Listener {
        fun onChangeCountry()
        fun onAction(app: BlockedApp, action: AppAction)
        fun onMore(app: BlockedApp, actions: List<AppAction>, anchor: View)
        fun onUpdateList()
    }

    sealed interface Row {
        val key: String

        data class Header(val countryName: String, val source: String, val notice: String?) : Row {
            override val key get() = "header"
        }

        data class Section(val title: String) : Row {
            override val key get() = "section:$title"
        }

        data class Message(val text: String) : Row {
            override val key get() = "message"
        }

        data class App(
            val app: BlockedApp,
            val status: String?,
            val summary: String,
            val installed: Boolean,
            val noPlay: Boolean,
        ) : Row {
            override val key get() = "app:${app.id}"
        }

        data class Footer(val text: String) : Row {
            override val key get() = "footer"
        }
    }

    private object Diff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
    }

    private var downloads: Map<String, AppDownloads.State> = emptyMap()

    /** Shows new download progress without redrawing the whole list. */
    fun setDownloads(states: Map<String, AppDownloads.State>) {
        val changed = (downloads.keys + states.keys).filter { downloads[it] != states[it] }.toSet()
        downloads = states
        currentList.forEachIndexed { index, row ->
            if (row is Row.App && row.app.id in changed) notifyItemChanged(index, PAYLOAD_DOWNLOAD)
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is Row.Header -> TYPE_HEADER
        is Row.Section -> TYPE_SECTION
        is Row.Message -> TYPE_MESSAGE
        is Row.App -> TYPE_APP
        is Row.Footer -> TYPE_FOOTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_blocked_header, parent, false))
            TYPE_SECTION -> TextHolder(inflater.inflate(R.layout.item_blocked_section, parent, false))
            TYPE_MESSAGE -> TextHolder(inflater.inflate(R.layout.item_blocked_empty, parent, false))
            TYPE_APP -> AppHolder(inflater.inflate(R.layout.item_blocked_app, parent, false))
            else -> FooterHolder(inflater.inflate(R.layout.item_blocked_footer, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is Row.Header -> (holder as HeaderHolder).bind(row)
            is Row.Section -> (holder as TextHolder).text.text = row.title
            is Row.Message -> (holder as TextHolder).text.text = row.text
            is Row.App -> (holder as AppHolder).bind(row, downloads[row.app.id])
            is Row.Footer -> (holder as FooterHolder).bind(row)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val row = getItem(position)
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_DOWNLOAD } && holder is AppHolder && row is Row.App) {
            holder.bindDownload(row, downloads[row.app.id])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val countryName: TextView = view.findViewById(R.id.country_name)
        private val source: TextView = view.findViewById(R.id.country_source)
        private val notice: TextView = view.findViewById(R.id.notice)
        private val change: MaterialButton = view.findViewById(R.id.change_button)

        fun bind(row: Row.Header) {
            countryName.text = row.countryName
            source.text = row.source
            notice.text = row.notice
            notice.visibility = if (row.notice.isNullOrEmpty()) View.GONE else View.VISIBLE
            change.setOnClickListener { listener.onChangeCountry() }
        }
    }

    private class TextHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view as TextView
    }

    private inner class FooterHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.footer_text)
        private val update: MaterialButton = view.findViewById(R.id.update_button)

        fun bind(row: Row.Footer) {
            text.text = row.text
            update.setOnClickListener { listener.onUpdateList() }
        }
    }

    private inner class AppHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: TextView = view.findViewById(R.id.avatar)
        private val name: TextView = view.findViewById(R.id.name)
        private val status: TextView = view.findViewById(R.id.status)
        private val summary: TextView = view.findViewById(R.id.summary)
        private val action: MaterialButton = view.findViewById(R.id.action)
        private val more: MaterialButton = view.findViewById(R.id.more)
        private val progressGroup: View = view.findViewById(R.id.progress_group)
        private val progress: LinearProgressIndicator = view.findViewById(R.id.progress)
        private val progressText: TextView = view.findViewById(R.id.progress_text)

        fun bind(row: Row.App, state: AppDownloads.State?) {
            val app = row.app
            avatar.text = app.name.take(1).uppercase(Locale.ROOT)
            name.text = app.name
            summary.text = row.summary
            summary.visibility = if (row.summary.isEmpty()) View.GONE else View.VISIBLE
            bindStatus(row.status)
            bindDownload(row, state)
        }

        private fun bindStatus(value: String?) {
            val context = status.context
            val (label, container, onContainer) = when (value) {
                AppCatalogUtils.STATUS_BLOCKED -> Triple(
                    R.string.app_status_blocked,
                    MaterialColors.getColor(status, com.google.android.material.R.attr.colorErrorContainer),
                    MaterialColors.getColor(status, com.google.android.material.R.attr.colorOnErrorContainer),
                )
                AppCatalogUtils.STATUS_RESTRICTED -> Triple(
                    R.string.app_status_restricted,
                    ContextCompat.getColor(context, R.color.md_warning_container),
                    ContextCompat.getColor(context, R.color.md_on_warning_container),
                )
                else -> {
                    status.visibility = View.GONE
                    return
                }
            }
            status.visibility = View.VISIBLE
            status.setText(label)
            status.backgroundTintList = ColorStateList.valueOf(container)
            status.setTextColor(onContainer)
        }

        fun bindDownload(row: Row.App, state: AppDownloads.State?) {
            val app = row.app
            val primary = AppActions.primary(app, row.installed, state, row.noPlay)
            action.visibility = if (primary == null) View.GONE else View.VISIBLE
            if (primary != null) action.setText(label(primary))
            action.setOnClickListener { if (primary != null) listener.onAction(app, primary) }

            val others = AppActions.others(app, primary, row.noPlay)
            more.visibility = if (others.isEmpty()) View.GONE else View.VISIBLE
            more.setOnClickListener { listener.onMore(app, others, it) }

            val context = progressText.context
            when (state) {
                AppDownloads.State.Preparing -> showProgress(null, context.getString(R.string.app_download_preparing))
                AppDownloads.State.Checking -> showProgress(null, context.getString(R.string.app_download_checking))
                AppDownloads.State.Installing, is AppDownloads.State.Confirm ->
                    showProgress(null, context.getString(R.string.app_install_preparing))
                is AppDownloads.State.Downloading -> if (state.total > 0) {
                    val percent = (state.bytes * 100 / state.total).toInt().coerceIn(0, 100)
                    showProgress(percent, context.getString(R.string.app_download_progress, percent))
                } else {
                    showProgress(null, context.getString(R.string.app_download_size, Formatter.formatShortFileSize(context, state.bytes)))
                }
                else -> progressGroup.visibility = View.GONE
            }
        }

        private fun showProgress(percent: Int?, text: String) {
            progressGroup.visibility = View.VISIBLE
            progressText.text = text
            if (percent != null) {
                progress.isIndeterminate = false
                progress.setProgressCompat(percent, true)
            } else if (!progress.isIndeterminate) {
                // The indicator can only become indeterminate while it is hidden
                progress.visibility = View.INVISIBLE
                progress.isIndeterminate = true
                progress.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SECTION = 1
        private const val TYPE_MESSAGE = 2
        private const val TYPE_APP = 3
        private const val TYPE_FOOTER = 4
        private const val PAYLOAD_DOWNLOAD = "download"

        fun label(action: AppAction): Int = when (action) {
            AppAction.Open -> R.string.app_action_open
            AppAction.Install, AppAction.InstallDownloaded -> R.string.app_action_install
            AppAction.Cancel -> android.R.string.cancel
            AppAction.Play -> R.string.app_action_play
            AppAction.Website -> R.string.app_action_website
        }
    }
}
