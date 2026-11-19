package com.aditv.core

import android.util.Log
import com.aditv.BuildConfig

/** Journal applicatif : tout passe par le Redactor, rien en build release sauf les erreurs. */
object AppLog {
    private const val TAG = "ADITV"
    fun d(msg: String) { if (BuildConfig.DEBUG) Log.d(TAG, Redactor.redact(msg)) }
    fun w(msg: String, t: Throwable? = null) { Log.w(TAG, Redactor.redact(msg + (t?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: ""))) }
    fun e(msg: String, t: Throwable? = null) { Log.e(TAG, Redactor.redact(msg + (t?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: ""))) }
}
