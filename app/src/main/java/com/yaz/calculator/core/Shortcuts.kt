package com.yaz.calculator.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.yaz.calculator.MainActivity
import com.yaz.calculator.R

/** The app's shortcuts on its icon (a long press): the converter, a new sheet. */
object Shortcuts {

    const val ACTION_CONVERT = "com.yaz.calculator.action.CONVERT"
    const val ACTION_SHEET = "com.yaz.calculator.action.SHEET"

    /** Set when the app opens (once is enough, they stay). */
    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        if (manager.dynamicShortcuts.size >= 2) return
        fun shortcut(id: String, label: String, action: String, glyph: Int) = ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setIcon(Icon.createWithResource(context, glyph))
            .setIntent(Intent(action).setComponent(ComponentName(context, MainActivity::class.java)))
            .build()
        runCatching {
            manager.dynamicShortcuts = listOf(
                shortcut("convert", "Convert", ACTION_CONVERT, R.drawable.ic_shortcut_convert),
                shortcut("sheet", "New sheet", ACTION_SHEET, R.drawable.ic_shortcut_sheet)
            )
        }
    }
}
