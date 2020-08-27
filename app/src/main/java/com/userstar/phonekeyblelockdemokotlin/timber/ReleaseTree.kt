package com.userstar.phonekeyblelockdemokotlin.timber

import android.util.Log

class ReleaseTree : ThreadIncludedDebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return !(priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO)
    }

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        if (!isLoggable(tag, priority)) {
            return
        }
        super.log(priority, tag, message, t)
    }
}