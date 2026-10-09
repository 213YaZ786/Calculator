package com.yaz.calculator.core.common

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Replaces this file with [bytes] in one step, or not at all, as writeTextAtomically does for text. */
internal fun File.writeBytesAtomically(bytes: ByteArray) {
    val temp = File(parentFile, "$name.tmp")
    FileOutputStream(temp).use { out ->
        out.write(bytes)
        out.fd.sync()
    }
    Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
