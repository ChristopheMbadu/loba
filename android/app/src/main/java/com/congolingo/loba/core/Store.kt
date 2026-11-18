package com.congolingo.loba.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.congolingo.loba.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Everything the screens read: the learner's progress, the lessons, and who is signed in.
 * Its properties are Compose state: a screen that reads them is redrawn when they change. Change them on the main thread.
 */
object AppStore {
    var state by mutableStateOf(GameState())
        private set
    var catalog by mutableStateOf(Catalog(emptyList(), emptyMap()))
        private set
    /**
     * Who is signed in. Remembered on the phone, so the app opens straight onto their lessons
     * instead of showing the welcome screen while it asks the server.
     */
    var learner by mutableStateOf<Learner?>(null)
        private set
    /** False until the first answer from the account service (or its failure). */
    var ready by mutableStateOf(false)
        private set
    /** Learning on this phone only, without an account. */
    var guest by mutableStateOf(false)
        private set
    var googleAvailable by mutableStateOf(false)

    private val prefs get() = Prefs.store
    private var unsynced: Boolean
        get() = prefs.getBoolean("loba-unsynced", false)
        set(value) = prefs.edit().putBoolean("loba-unsynced", value).apply()
    private val scope = MainScope()
    private var pushJob: Job? = null
    private var applying = false

    /** Called once, when the app process starts. */
    fun load() {
        val saved = prefs.getString("loba-state", null)?.let { try { JSONObject(it) } catch (_: Exception) { null } }
        var initial = GameState.from(saved) ?: GameState()
        if (saved == null && Locale.getDefault().language != "fr") initial = initial.copy(ui = UiLang.EN)
        state = initial
        catalog = CatalogParser.bundled()
        guest = prefs.getBoolean("loba-guest", false)
        learner = prefs.getString("loba-learner", null)?.let { try { Learner.from(JSONObject(it)) } catch (_: Exception) { null } }
        tick()
    }

    private fun remember(who: Learner?) {
        if (who == learner) return
        learner = who
        if (who != null) prefs.edit().putString("loba-learner", who.json.toString()).apply() else prefs.edit().remove("loba-learner").apply()
    }

    val units: List<LessonUnit> get() = catalog.units(state.lang)
    val language: Language? get() = catalog.languages.firstOrNull { it.code == state.lang }
    val signedIn: Boolean get() = learner != null
    /** May use the learning screens: signed in, or chose to learn on this phone. */
    val admitted: Boolean get() = signedIn || guest
    val today: String get() = Game.dayKey()

    // launch

    suspend fun start() = coroutineScope {
        val fresh = async { Api.catalog() }
        val google = async { Api.googleForApp() }
        try {
            val session = Api.me()
            if (session != null) {
                remember(session.learner)
                if (unsynced && state.onboarded) push() else adopt(session.progress)
            } else {
                // The server no longer knows this phone (signed out elsewhere, or the session ended).
                remember(null)
            }
        } catch (_: ApiError) {
            // Offline: keep going with what is on the phone.
        }
        fresh.await()?.let { catalog = it }
        googleAvailable = google.await()
        fixLanguage()
        ready = true
    }

    /** If the learner's language was hidden in Studio, continue with one that is available. */
    private fun fixLanguage() {
        val first = catalog.languages.firstOrNull() ?: return
        if (catalog.curriculum[state.lang] == null) update { it.copy(lang = first.code) }
    }

    // state changes. All of them go through `update`, which saves and syncs.

    private fun update(change: (GameState) -> GameState) {
        val next = change(state)
        if (next == state) return
        state = next
        prefs.edit().putString("loba-state", next.json.toString()).apply()
        if (applying) return
        unsynced = true
        if (!signedIn) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(2000)
            push()
        }
    }

    /** Daily rollover and heart regeneration, whenever the learner comes back. */
    fun tick() = update { Rules.rollDaily(Rules.syncHearts(it, Game.now()), Game.dayKey()) }

    fun finishOnboarding(lang: String, goal: Int) = update { it.copy(lang = lang, goal = goal, onboarded = true) }
    fun setUi(ui: UiLang) = update { it.copy(ui = ui) }
    fun setLang(lang: String) = update { it.copy(lang = lang) }
    fun setGoal(goal: Int) = update { it.copy(goal = goal) }
    fun setProfile(name: String, avatar: String) = update { it.copy(name = name.take(20), avatar = avatar) }
    fun toggleSound() = update { it.copy(sound = !it.sound) }
    fun toggleVoice() = update { it.copy(voice = !it.voice) }
    fun loseHeart() = update { Rules.loseHeart(it, Game.now()) }
    fun recordWord(term: String, correct: Boolean) = update { Rules.recordWord(it, it.lang, term, correct, Game.now()) }
    fun claim(quest: Rules.QuestId) = update { Rules.claimQuest(it, quest, Game.dayKey()) }
    fun buy(item: Rules.ShopItem) = update { Rules.buy(it, item, Game.now()) }
    fun openChest(unit: LessonUnit) = update { Rules.openChest(it, unit) }

    fun complete(result: Rules.SessionResult): Rules.Reward {
        val (next, reward) = Rules.completeSession(state, result, Game.now(), Game.dayKey())
        update { next }
        return reward
    }

    fun eraseProgress() {
        val ui = state.ui
        update { GameState(ui = ui) }
        PuzzleScores.clear()
        MapProgress.clear()
    }

    // account

    /** Replaces this phone's progress with the account's (signing in on a new device). */
    private fun adopt(progress: Any?) {
        val saved = GameState.from(progress) ?: return
        applying = true
        update { saved }
        applying = false
        unsynced = false
        tick()
    }

    private suspend fun push() {
        if (!signedIn || !unsynced) return
        try {
            Api.saveProgress(state.json)
            unsynced = false
        } catch (_: ApiError) {
            // Offline: the flag stays set and the next change or launch retries.
        }
    }

    /** Called when the app goes to the background: save now, don't wait for the timer. */
    fun flush() {
        pushJob?.cancel()
        scope.launch { push() }
    }

    fun signedUp(session: Api.Session) {
        remember(session.learner)
        if (session.progress == null) { unsynced = true; scope.launch { push() } } else adopt(session.progress)
    }

    /** After signing in, the account's progress wins unless it has none yet. */
    fun signedInWith(session: Api.Session) {
        remember(session.learner)
        if (GameState.from(session.progress) != null) adopt(session.progress) else { unsynced = true; scope.launch { push() } }
        fixLanguage()
    }

    fun continueAsGuest() {
        guest = true
        prefs.edit().putBoolean("loba-guest", true).apply()
    }

    suspend fun signOut() {
        push()
        try { Api.logout() } catch (_: ApiError) {}
        forget()
    }

    /** Throws ApiError. */
    suspend fun deleteAccount(password: String) {
        Api.deleteAccount(password)
        forget()
    }

    /** Throws ApiError. */
    suspend fun rename(name: String) {
        Api.rename(name)
        learner?.let { remember(it.copy(name = name)) }
    }

    /** Forgets everything stored on this phone (signing out on a shared phone). */
    private fun forget() {
        pushJob?.cancel()
        remember(null)
        guest = false
        prefs.edit().putBoolean("loba-guest", false).apply()
        applying = true
        eraseProgress()
        applying = false
        unsynced = false
    }
}

/** The current interface language, for strings written inline: `L("Bonjour", "Hello")`. */
fun L(fr: String, en: String): String = if (AppStore.state.ui == UiLang.FR) fr else en

// Evening reminder

object Reminder {
    private const val key = "loba-reminder"
    private const val work = "loba-daily"
    const val channel = "loba-daily"

    /** Set by MainActivity: asks Android for permission to show notifications. */
    var askPermission: ((done: (Boolean) -> Unit) -> Unit)? = null

    val isOn: Boolean get() = Prefs.store.getString(key, null) == "on"

    private suspend fun allowed(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val ask = askPermission ?: return false
        val answer = CompletableDeferred<Boolean>()
        ask { answer.complete(it) }
        return answer.await()
    }

    /** Turns the 7 pm reminder on or off. Returns whether it is on afterwards. */
    suspend fun set(on: Boolean): Boolean {
        val manager = WorkManager.getInstance(appContext)
        manager.cancelUniqueWork(work)
        val enabled = on && allowed()
        if (enabled) {
            val now = LocalDateTime.now()
            var next = now.withHour(19).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build()
            manager.enqueueUniquePeriodicWork(work, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        Prefs.store.edit().putString(key, if (enabled) "on" else "off").apply()
        return enabled
    }

    /** After a first finished lesson, offer the reminder once. */
    fun afterLesson() {
        if (Prefs.store.getString(key, null) == null) MainScope().launch { set(true) }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        // Someone who already learned today doesn't need reminding.
        if (AppStore.state.lastActiveDay == Game.dayKey()) return Result.success()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(Reminder.channel, L("Rappel du soir", "Evening reminder"), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = NotificationCompat.Builder(context, Reminder.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(L("Loba t’attend !", "Loba is waiting!"))
            .setContentText(L("Cinq minutes suffisent pour garder ta série.", "Five minutes is enough to keep your streak."))
            .setAutoCancel(true)
            .setContentIntent(open?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) })
            .build()
        try { manager.notify(1, notification) } catch (_: SecurityException) {}
        return Result.success()
    }
}
