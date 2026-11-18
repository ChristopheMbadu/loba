package com.congolingo.loba.core

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Per-attempt proof and callback state. No Google client secret belongs in the app. */
class GoogleAuthRequest {
    enum class CallbackIssue(val raw: String) {
        FORMAT("FORMAT"), SCHEME("SCHEME"), HOST("HOST"), PATH("PATH"), CREDENTIALS("CREDENTIALS"), PORT("PORT"),
        FRAGMENT("FRAGMENT"), QUERY("QUERY"), PAYLOAD("PAYLOAD"),
    }

    sealed class Failure : Exception() {
        data class InvalidCallback(val issue: CallbackIssue) : Failure()
        data object InvalidState : Failure()
        data object InvalidCode : Failure()
        data class Provider(val reason: String) : Failure()
    }

    val verifier: String = token()
    val state: String = token()

    val challenge: String
        get() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)))

    fun startURL(origin: String): String = "${origin.trimEnd('/')}/api/learn/google/start?app=1&challenge=$challenge&app_state=$state"

    fun code(from: String): String {
        // Parse once so all validation uses the same URL representation.
        val parts = try { URI(from) } catch (_: Exception) { throw Failure.InvalidCallback(CallbackIssue.FORMAT) }
        if (parts.scheme?.lowercase() != "loba") throw Failure.InvalidCallback(CallbackIssue.SCHEME)
        // "user@auth" and "auth:443" both leave `host` readable here; the authority is checked as a whole below.
        val authority = parts.rawAuthority ?: throw Failure.InvalidCallback(CallbackIssue.HOST)
        if ("@" in authority) throw Failure.InvalidCallback(CallbackIssue.CREDENTIALS)
        if (":" in authority) throw Failure.InvalidCallback(CallbackIssue.PORT)
        if (authority.lowercase() != "auth") throw Failure.InvalidCallback(CallbackIssue.HOST)
        val path = parts.rawPath ?: ""
        if (path.isNotEmpty() && path != "/") throw Failure.InvalidCallback(CallbackIssue.PATH)
        // An empty fragment carries no data; nonempty fragments remain prohibited.
        if (!parts.rawFragment.isNullOrEmpty()) throw Failure.InvalidCallback(CallbackIssue.FRAGMENT)
        val query = parts.rawQuery ?: throw Failure.InvalidCallback(CallbackIssue.QUERY)
        val items = query.split("&").filter { it.isNotEmpty() }.map {
            val name = it.substringBefore("=")
            val value = if ("=" in it) it.substringAfter("=") else null
            URLDecoder.decode(name, "UTF-8") to value?.let { v -> URLDecoder.decode(v, "UTF-8") }
        }
        fun unique(name: String): String? = items.filter { it.first == name }.let { if (it.size == 1) it[0].second else null }
        if (unique("state") != state) throw Failure.InvalidState
        if (items.any { it.first == "error" }) {
            val reason = unique("error")
            if (reason == null || items.any { it.first == "code" }) throw Failure.InvalidCallback(CallbackIssue.PAYLOAD)
            throw Failure.Provider(reason)
        }
        val code = unique("code")
        if (code == null || !codePattern.matches(code)) throw Failure.InvalidCode
        return code
    }

    private companion object {
        const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val codePattern = Regex("^[A-Za-z0-9_-]{43}$")
        val random = SecureRandom()
        fun token(): String = String(CharArray(43) { alphabet[random.nextInt(alphabet.length)] })
    }
}
