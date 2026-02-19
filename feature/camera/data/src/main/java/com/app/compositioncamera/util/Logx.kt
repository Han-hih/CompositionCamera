package com.app.compositioncamera.util

import timber.log.Timber

object Logx {
    fun init(isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
    }

    fun d(message: String, tag: String? = null) {
        if (tag.isNullOrBlank()) {
            Timber.d(message)
        } else {
            Timber.tag(tag).d(message)
        }
    }

    fun i(message: String, tag: String? = null) {
        if (tag.isNullOrBlank()) {
            Timber.i(message)
        } else {
            Timber.tag(tag).i(message)
        }
    }

    fun w(message: String, tag: String? = null) {
        if (tag.isNullOrBlank()) {
            Timber.w(message)
        } else {
            Timber.tag(tag).w(message)
        }
    }

    fun e(throwable: Throwable? = null, message: String, tag: String? = null) {
        if (tag.isNullOrBlank()) {
            if (throwable != null) {
                Timber.e(throwable, message)
            } else {
                Timber.e(message)
            }
        } else {
            if (throwable != null) {
                Timber.tag(tag).e(throwable, message)
            } else {
                Timber.tag(tag).e(message)
            }
        }
    }
}
