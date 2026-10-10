package com.yaz.calculator.engine.giac

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/**
 * Giac, the computer algebra system, in a process of its own with no
 * permission, no files and no network: whatever an expression makes it do,
 * it reaches nothing of the user's, and a crash takes only this process.
 *
 * Two calls over a plain Binder, as the shared DecoderService: EVAL, a text
 * in and the value's text out; INTERRUPT, from another thread, stops it.
 */
class GiacService : Service() {

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                EVAL -> {
                    data.enforceInterface(DESCRIPTOR)
                    val text = data.readString().orEmpty()
                    val out = if (text.length > MAX_INPUT) "GIAC_ERROR: too long" else eval(text)
                    reply?.writeString(if (out.length > MAX_OUTPUT) "GIAC_ERROR: too long" else out)
                }
                INTERRUPT -> {
                    data.enforceInterface(DESCRIPTOR)
                    interrupt()
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val DESCRIPTOR = "com.yaz.calculator.engine.giac.Giac"
        const val EVAL = IBinder.FIRST_CALL_TRANSACTION
        const val INTERRUPT = IBinder.FIRST_CALL_TRANSACTION + 1
        private const val MAX_INPUT = 4_000
        private const val MAX_OUTPUT = 60_000

        init { System.loadLibrary("calcgiac") }

        @JvmStatic external fun eval(text: String): String
        @JvmStatic external fun interrupt()
    }
}
