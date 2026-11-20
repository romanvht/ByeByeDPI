package io.github.romanvht.byedpi.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.content.withStyledAttributes

/** A LinearLayout that never grows wider than `android:maxWidth`, so content stays readable on tablets and TVs. */
class MaxWidthLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var maxWidth = Int.MAX_VALUE

    init {
        context.withStyledAttributes(attrs, intArrayOf(android.R.attr.maxWidth), defStyleAttr, 0) {
            maxWidth = getDimensionPixelSize(0, Int.MAX_VALUE)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val spec = if (width > maxWidth) {
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.getMode(widthMeasureSpec))
        } else {
            widthMeasureSpec
        }
        super.onMeasure(spec, heightMeasureSpec)
    }
}
