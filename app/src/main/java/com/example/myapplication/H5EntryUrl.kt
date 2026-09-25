package com.example.myapplication

import android.net.Uri

/** The native container already reserves system-bar insets around its WebView. */
object H5EntryUrl {
    fun forApp(entryUrl: String): String = runCatching {
        val uri = Uri.parse(entryUrl)
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.getQueryParameter("topInset") != null) entryUrl
        else uri.buildUpon().appendQueryParameter("topInset", "host").build().toString()
    }.getOrDefault(entryUrl)
}
