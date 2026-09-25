package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myapplication.h5.H5PackageManager

/** Reconcile saved selection after an APK adds/removes game aliases, without resetting it. */
class PackageUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val manager = H5PackageManager(context)
        try { manager.syncLauncherAliasWithSelection() } finally { manager.shutdown() }
    }
}
