package app.phonetube.util

import android.util.Log

object AppLogger {
    private const val TAG = "PhoneTube"

    fun d(category: String, message: String) {
        Log.d(TAG, "[$category] $message")
    }

    fun w(category: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(TAG, "[$category] $message", throwable)
        } else {
            Log.w(TAG, "[$category] $message")
        }
    }

    fun e(category: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[$category] $message", throwable)
        } else {
            Log.e(TAG, "[$category] $message")
        }
    }
}
