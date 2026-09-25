package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class H5EntryUrlTest {
    @Test fun defaultsToHostInsetsWithoutChangingExistingUrlComponents() {
        assertEquals("https://money.lucc.site:8888/?topInset=host", H5EntryUrl.forApp("https://money.lucc.site:8888/"))
        assertEquals("https://example.com/play?save=a%2Fb&topInset=host#town", H5EntryUrl.forApp("https://example.com/play?save=a%2Fb#town"))
        assertEquals("https://example.com/?topInset=web#town", H5EntryUrl.forApp("https://example.com/?topInset=web#town"))
        assertEquals("file:///android_asset/bundles/aurora/www/index.html", H5EntryUrl.forApp("file:///android_asset/bundles/aurora/www/index.html"))
    }
}
