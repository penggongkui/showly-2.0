package com.michaldrabik.ui_base.common.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.utilities.extensions.colorFromAttr
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx

class BadgeTextView : AppCompatTextView {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    setBackgroundResource(R.drawable.bg_badge)
    setTextColor(context.colorFromAttr(android.R.attr.textColorPrimary))
    setTypeface(null, Typeface.BOLD)
    ViewCompat.setElevation(this, context.dimenToPx(R.dimen.elevationTiny).toFloat())
    updatePadding(
      left = context.dimenToPx(R.dimen.spaceSmall),
      top = context.dimenToPx(R.dimen.spaceMicro),
      right = context.dimenToPx(R.dimen.spaceSmall),
      bottom = context.dimenToPx(R.dimen.spaceMicro)
    )
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13F)
    maxLines = 1
    textAlignment = TEXT_ALIGNMENT_VIEW_START
  }
}
