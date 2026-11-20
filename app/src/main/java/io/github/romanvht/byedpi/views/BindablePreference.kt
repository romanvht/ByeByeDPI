package io.github.romanvht.byedpi.views

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/** A preference with a custom layout whose views are set up in [onBind] every time it is shown. */
class BindablePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    var onBind: ((PreferenceViewHolder) -> Unit)? = null
        set(value) {
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        onBind?.invoke(holder)
    }
}
