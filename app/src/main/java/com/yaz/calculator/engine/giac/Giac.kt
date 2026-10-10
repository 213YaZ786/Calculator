package com.yaz.calculator.engine.giac

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The app's side of GiacService: one computation at a time, off the main
 * thread, stopped after [eval]'s time; the service is bound on the first
 * call and let go 30 s after the last, so its process does not linger.
 * Without the service (unit tests on the JVM) every call gives null.
 */
object Giac {
    private var app: Context? = null
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val lock = ReentrantLock()
    @Volatile private var binder: IBinder? = null
    @Volatile private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { binder = service; ready?.countDown() }
        override fun onServiceDisconnected(name: ComponentName?) { binder = null }
        override fun onBindingDied(name: ComponentName?) { release() }
    }
    @Volatile private var ready: CountDownLatch? = null

    private val letGo = Runnable { release() }

    fun attach(context: Context) { app = context.applicationContext }

    /** The value of [text] in Giac's language, or null (no service, an error, too long). */
    fun eval(text: String, seconds: Long = 8): String? {
        val context = app ?: return null
        check(Looper.myLooper() != Looper.getMainLooper()) { "Giac off the main thread" }
        return lock.withLock {
            main.removeCallbacks(letGo)
            val service = connect(context) ?: return@withLock null
            // A watchdog interrupts Giac when the time is up.
            val stop = Runnable { runCatching { call(service, GiacService.INTERRUPT, null) } }
            main.postDelayed(stop, seconds * 1000)
            val out = runCatching { call(service, GiacService.EVAL, text) }.getOrNull()
            main.removeCallbacks(stop)
            main.postDelayed(letGo, 30_000)
            out?.takeIf { !it.startsWith("GIAC_ERROR") && it != "undef" && !it.contains("Error") }
        }
    }

    private fun connect(context: Context): IBinder? {
        binder?.takeIf { it.isBinderAlive }?.let { return it }
        val latch = CountDownLatch(1)
        ready = latch
        main.post {
            if (!bound) bound = context.bindService(Intent(context, GiacService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
        latch.await(5, TimeUnit.SECONDS)
        return binder
    }

    private fun call(service: IBinder, code: Int, text: String?): String? {
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(GiacService.DESCRIPTOR)
            if (text != null) data.writeString(text)
            service.transact(code, data, reply, 0)
            return if (code == GiacService.EVAL) reply.readString() else null
        } finally {
            data.recycle(); reply.recycle()
        }
    }

    /** Lets the service go: its process is then the system's to end. */
    fun release() {
        main.post {
            if (bound) runCatching { app?.unbindService(connection) }
            bound = false
            binder = null
        }
    }
}
