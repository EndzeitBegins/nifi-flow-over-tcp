package io.github.endzeitbegins.nifi.flowovertcp.utils

import java.security.MessageDigest

internal val ByteArray.sha256: String
    get() {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(this)

        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }