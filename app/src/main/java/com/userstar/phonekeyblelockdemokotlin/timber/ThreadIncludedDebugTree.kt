package com.userstar.phonekeyblelockdemokotlin.timber

import timber.log.Timber

open class ThreadIncludedDebugTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        super.log(priority, tag, message, t)
    }

    override fun createStackElementTag(element: StackTraceElement): String? {
        return super.createStackElementTag(element) + "(" + element.lineNumber + ") "
    }
}