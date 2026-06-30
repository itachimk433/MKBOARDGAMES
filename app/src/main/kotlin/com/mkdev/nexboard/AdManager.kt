package com.mkdev.nexboard

import android.content.Context
import android.widget.LinearLayout

object AdManager {
    internal const val ADS_ENABLED = false
    fun attachBanner(container: LinearLayout) {}
    fun loadInterstitial(context: Context, onResult: (Any?) -> Unit) { onResult(null) }
    fun showInterstitial(context: Context, ad: Any?) {}
}
