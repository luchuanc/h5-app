package com.example.myapplication.h5

data class LaunchBundleOption(
    val id: String,
    val title: String,
    val description: String,
    val version: String,
    val entryUrl: String,
    val mode: String,
    val source: String,
    val installedAt: Long? = null,
    val launcherAliasId: String? = null,
    val appDisplayName: String? = null
) {
    fun toBundleInfo(): H5BundleInfo = H5BundleInfo(
        mode = mode,
        version = version,
        entryUrl = entryUrl,
        installedAt = installedAt,
        source = source
    )
}
