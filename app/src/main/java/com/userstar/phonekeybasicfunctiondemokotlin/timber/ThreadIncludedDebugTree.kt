package com.userstar.phonekeybasicfunctiondemokotlin.timber

import timber.log.Timber

open class ThreadIncludedDebugTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        var tag = tag
        if (tag != null) {
            tag = "$tag"
        }
        super.log(priority, tag, message, t)
    }

    override fun createStackElementTag(element: StackTraceElement): String? {
        return super.createStackElementTag(element) + "(" + element.lineNumber + ") "
    }
}