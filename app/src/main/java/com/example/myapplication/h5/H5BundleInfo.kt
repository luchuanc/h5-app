package com.example.myapplication.h5

data class H5BundleInfo(
    val mode: String,
    val version: String,
    val entryUrl: String,
    val installedAt: Long? = null,
    val source: String? = null
)
