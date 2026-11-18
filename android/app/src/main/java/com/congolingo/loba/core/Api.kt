package com.congolingo.loba.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Learner(val name: String, val email: String, val googleLinked: Boolean, val passwordLogin: Boolean) {
    val json: JSONObject get() = JSONObject().put("name", name).put("email", email).put("googleLinked", googleLinked).put("passwordLogin", passwordLogin)

    companion object {
        fun from(v: Any?): Learner? {
            val o = v as? JSONObject ?: return null
            val email = o.opt("email") as? String ?: return null
            return Learner(o.opt("name") as? String ?: "", email, o.opt("googleLinked") as? Boolean ?: false, o.opt("passwordLogin") as? Boolean ?: true)
        }
    }
}

class ApiError(val status: Int, override val message: String) : Exception(message) {
    /** The server could not be reached at all. */
    val offline: Boolean get() = status == 0
}

/** The sign-in cookie, kept between launches (iOS does this for the app by itself). */
private object Cookies : CookieJar {
    private const val key = "loba-cookies"
    private var loaded: MutableList<Cookie>? = null

    @Synchronized
    private fun all(): MutableList<Cookie> {
        loaded?.let { return it }
        val url = Api.origin.toHttpUrlOrThrow()
        val list = try {
            JSONArray(Prefs.store.getString(key, null) ?: "[]").items().mapNotNull { Cookie.parse(url, it as? String ?: "") }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
        loaded = list
        return list
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val list = all()
        for (c in cookies) {
            list.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            if (c.expiresAt > System.currentTimeMillis()) list += c
        }
        Prefs.store.edit().putString(key, JSONArray(list.map { it.toString() }).toString()).apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = all().filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }

    private fun String.toHttpUrlOrThrow(): HttpUrl = HttpUrl.Builder().scheme("https").host(removePrefix("https://")).build()
}

/** Talks to the Loba server. The session lives in a cookie that the app keeps. */
object Api {
    /** The address learners use on the web. Sign-in cookies belong to this host. */
    const val origin = "https://congolingo.com"

    private val jsonType = "application/json".toMediaType()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder().cookieJar(Cookies).callTimeout(15, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
    }

    data class Session(val learner: Learner, val progress: Any?)

    fun url(path: String): String = origin + (if (path.startsWith("/")) path else "/$path")

    private suspend fun call(path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(path)).header("Accept", "application/json")
        if (method != "GET") {
            // The server only accepts writes that name it as their origin. This app is its own client, so it says so.
            req.header("Origin", origin)
            req.method(method, (body ?: JSONObject()).toString().toRequestBody(jsonType))
        }
        val (status, text) = try {
            client.newCall(req.build()).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (_: Exception) {
            throw ApiError(0, "offline")
        }
        val json = try { JSONObject(text) } catch (_: Exception) { null }
        if (status !in 200..299 || json == null) throw ApiError(if (json == null) 0 else status, json?.opt("error") as? String ?: "offline")
        json
    }

    /** A plain GET of a public file. Null on any failure. */
    suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { if (it.code == 200) it.body?.bytes() else null }
        } catch (_: Exception) { null }
    }

    suspend fun catalog(): Catalog? = fetch(url("/api/v1/lessons"))?.let { CatalogParser.published(String(it, Charsets.UTF_8)) }

    private fun progress(body: JSONObject): Any? = (body.opt("progress") as? JSONObject)?.opt("state")?.takeIf { it != JSONObject.NULL }

    /** The signed-in learner, null when signed out. Throws when the server can't be reached. */
    suspend fun me(): Session? {
        val body = call("/api/learn/me")
        val learner = Learner.from(body.opt("learner")) ?: return null
        return Session(learner, progress(body))
    }

    private fun session(body: JSONObject): Session {
        val learner = Learner.from(body.opt("learner")) ?: throw ApiError(0, "offline")
        return Session(learner, progress(body))
    }

    suspend fun signup(name: String, email: String, password: String, state: JSONObject?): Session {
        val input = JSONObject().put("name", name).put("email", email).put("password", password)
        if (state != null) input.put("state", state)
        return session(call("/api/learn/signup", "POST", input))
    }

    suspend fun login(email: String, password: String): Session =
        session(call("/api/learn/login", "POST", JSONObject().put("email", email).put("password", password)))

    suspend fun logout() { call("/api/learn/logout", "POST") }

    suspend fun rename(name: String) { call("/api/learn/profile", "PUT", JSONObject().put("name", name)) }

    suspend fun deleteAccount(password: String) { call("/api/learn/account", "DELETE", JSONObject().put("password", password)) }

    suspend fun saveProgress(state: JSONObject) { call("/api/learn/progress", "PUT", JSONObject().put("state", state)) }

    /** Whether this server offers Google sign-in to the app (it must hand the session back to the app, not to a browser). */
    suspend fun googleForApp(): Boolean {
        val body = try { call("/api/learn/auth-options") } catch (_: ApiError) { return false }
        return body.opt("google") == true && body.opt("app") == true && body.opt("appProtocol") == 2
    }

    /** Swaps the one-time code the server sent back after Google sign-in for a session. */
    suspend fun redeemGoogle(code: String, verifier: String): Session =
        session(call("/api/learn/google/redeem", "POST", JSONObject().put("code", code).put("verifier", verifier)))

    fun audioURL(id: String): String = url("/api/v1/audio/$id")
}
