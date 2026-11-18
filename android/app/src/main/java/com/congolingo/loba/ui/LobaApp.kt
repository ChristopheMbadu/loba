package com.congolingo.loba.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.congolingo.loba.core.Api
import com.congolingo.loba.core.ApiError
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.DRC
import com.congolingo.loba.core.Game
import com.congolingo.loba.core.GoogleAuthRequest
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.L
import com.congolingo.loba.core.PracticeMode
import com.congolingo.loba.core.Sfx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun RootView() {
    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Sfx.warmUp(); AppStore.start() }
    LaunchedEffect(Unit) { DRC.refresh() }
    LaunchedEffect(Unit) {
        // Long enough to be seen. Someone already known to this phone goes straight in; otherwise wait
        // for the server's answer (a session may exist), but never more than a few seconds.
        delay(if (DebugFlags.quick) 0 else 3600)
        repeat(30) { if (!AppStore.admitted && !AppStore.ready) delay(100) }
        launched = true
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        val stage = if (!AppStore.admitted) 0 else if (!AppStore.state.onboarded) 1 else 2
        Crossfade(stage, animationSpec = tween(300), label = "root") {
            when (it) {
                0 -> LandingView()
                1 -> OnboardingView()
                else -> TabsView()
            }
        }
        DebugLaunch()
        AnimatedVisibility(!launched, enter = fadeIn(tween(0)), exit = fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 1.08f)) { SplashView() }
    }
}

/** For screenshots while developing: `demo` skips sign-in and onboarding, `screen` opens that screen. See DebugFlags. */
@Composable
private fun DebugLaunch() {
    var cover by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!DebugFlags.demo) return@LaunchedEffect
        AppStore.continueAsGuest()
        if (!AppStore.state.onboarded) AppStore.finishOnboarding("ln", 20)
        if (DebugFlags.screen in listOf("story", "lesson", "build", "review", "puzzle", "puzzles", "map")) cover = DebugFlags.screen
    }
    val name = cover ?: return
    val close = { cover = null }
    FullScreenCover(onDismiss = close) {
        val unit = AppStore.units.firstOrNull()
        when {
            name == "story" -> StoryView(onClose = close)
            name == "map" -> MapGameView(onClose = close)
            name == "puzzle" || name == "puzzles" -> PuzzleHub(autostart = name == "puzzle", onClose = close)
            name == "review" -> LessonView(LessonMode.Practice(PracticeMode.REVIEW), onClose = close)
            unit != null -> LessonView(LessonMode.Lesson(unit, if (name == "build") 3 else 0), onClose = close)
        }
    }
}

// MARK: - Welcome

@Composable
fun LandingView() {
    var auth by remember { mutableStateOf<AuthMode?>(null) }
    var story by remember { mutableStateOf(false) }
    var greeting by remember { mutableIntStateOf(0) }
    val appear = remember { Animatable(0f) }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1700, easing = EaseInOut), RepeatMode.Reverse), label = "bob",
    )
    val sway = if (Motion.still) 0f else bob
    val ui = AppStore.state.ui
    val greetings = AppStore.catalog.languages.filter { it.greeting.isNotEmpty() }
        .map { "${it.greeting} !" to it.name(ui).replaceFirstChar { c -> c.uppercase() } }
        .ifEmpty { listOf("Mbote !" to "Lingala") }

    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600)
            greeting += 1
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("loba", style = displayFont(30), color = Sky)
            Spacer(Modifier.weight(1f))
            UiSwitch()
        }

        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        Box(Modifier.graphicsLayer { val s = 0.85f + 0.15f * appear.value; scaleX = s; scaleY = s; alpha = appear.value.coerceIn(0f, 1f) }) {
            Box(Modifier.size(290.dp)) {
                // The sky, the sun and the river, all inside the disc.
                Box(Modifier.matchParentSize().clip(CircleShape).background(Brush.verticalGradient(listOf(hexColor("cfeafd"), SkyTint, hexColor("fff3d6"))))) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 14.dp).size(118.dp), contentAlignment = Alignment.Center) {
                        SunRays(Modifier.matchParentSize())
                        Box(Modifier.size(64.dp).shadow(16.dp, CircleShape, ambientColor = Sun, spotColor = Sun).background(Sun, CircleShape))
                    }
                    DaySky(Modifier.matchParentSize())
                    River(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp))
                }
                Pirogue(Modifier.align(Alignment.BottomCenter).offset(y = (-46).dp).size(250.dp, 52.dp))
                val rock = Modifier.graphicsLayer {
                    translationY = (-3f * sway).dp.toPx()
                    rotationZ = -1.5f * sway
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                LobaView(Mood.WAVE, 180.dp, modifier = Modifier.align(Alignment.Center).offset(y = (-8).dp).then(rock))
                Pirogue(Modifier.align(Alignment.BottomCenter).offset(y = (-46).dp).then(rock).size(250.dp, 52.dp), front = true)
            }
            AnimatedContent(
                greeting, Modifier.offset(x = (-18).dp, y = 4.dp), label = "greeting",
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.55f), initialScale = 0.6f, transformOrigin = TransformOrigin(1f, 1f)) + fadeIn()) togetherWith fadeOut(tween(150))
                },
            ) { n ->
                val g = greetings[n % greetings.size]
                Bubble {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(g.first, style = displayFont(24), color = SkyShade)
                        Text(g.second, style = textFont(14), color = Muted)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(L("Parle enfin la langue de la famille.", "Finally speak the family language."), style = displayFont(32), color = Ink, textAlign = TextAlign.Center)
            Text(
                L("Les langues du Congo, cinq minutes par jour. Gratuit, sans pub.", "The languages of Congo, five minutes a day. Free, no ads."),
                style = textFont(17), color = Ink2, textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ChunkyButton(L("Commencer", "Get started")) { auth = AuthMode.SIGNUP }
            ChunkyButton(L("J’ai déjà un compte", "I already have an account"), kind = Chunky.OUTLINE) { auth = AuthMode.LOGIN }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.tappable { story = true }.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(18.dp), tint = Ink2)
                    Text(L("Lire un conte", "Read a folktale"), style = textFont(14, W.BOLD), color = Ink2)
                }
                Box(Modifier.tappable { AppStore.continueAsGuest() }.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                    Text(L("Plus tard, sans compte", "Later, without an account"), style = textFont(14, W.BOLD), color = Ink2)
                }
            }
        }
    }

    auth?.let { mode -> Sheet(onDismiss = { auth = null }) { AuthView(mode, onClose = { auth = null }) } }
    if (story) FullScreenCover(onDismiss = { story = false }) { StoryView(onClose = { story = false }) }
}

/** A dugout canoe. Drawn twice: the whole hull behind Loba, then the near side in front, so Loba sits inside it. */
@Composable
fun Pirogue(modifier: Modifier = Modifier, front: Boolean = false) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (!front) {
            val inside = Path().apply {
                moveTo(0f, h * 0.18f)
                quadraticTo(w / 2, -h * 0.12f, w, h * 0.18f)
                quadraticTo(w / 2, h * 0.5f, 0f, h * 0.18f)
            }
            drawPath(inside, hexColor("6b3f1c"))
            return@Canvas
        }
        val hull = Path().apply {
            moveTo(0f, h * 0.18f)
            quadraticTo(w / 2, h * 0.5f, w, h * 0.18f)
            quadraticTo(w * 0.98f, h * 0.55f, w * 0.9f, h * 0.78f)
            quadraticTo(w / 2, h * 1.12f, w * 0.1f, h * 0.78f)
            quadraticTo(w * 0.02f, h * 0.55f, 0f, h * 0.18f)
        }
        drawPath(hull, hexColor("8c4f1f"))
        val rim = Path().apply { moveTo(0f, h * 0.18f); quadraticTo(w / 2, h * 0.5f, w, h * 0.18f) }
        drawPath(rim, hexColor("b5682a"), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        val band = Path().apply { moveTo(w * 0.12f, h * 0.5f); quadraticTo(w / 2, h * 0.82f, w * 0.88f, h * 0.5f) }
        drawPath(band, Sun, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Moving water: three bands of waves sliding past each other. */
@Composable
fun River(modifier: Modifier = Modifier) {
    val clock = rememberAmbient()
    Canvas(modifier) {
        val t = clock.value.toFloat()
        drawRect(Sky)
        val step = 6.dp.toPx()
        listOf(0.3f, 0.55f, 0.8f).forEachIndexed { i, y ->
            val wave = Path()
            val phase = t * (0.6f + i * 0.25f)
            var x = 0f
            while (x <= size.width) {
                val py = size.height * y + sin(x / 26.dp.toPx() + phase + i) * 4.dp.toPx()
                if (x == 0f) wave.moveTo(x, py) else wave.lineTo(x, py)
                x += step
            }
            drawPath(wave, Color.White.copy(alpha = 0.35f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

// MARK: - Sign up, sign in

enum class AuthMode { SIGNUP, LOGIN }

/** Shown inside a `Sheet`. */
@Composable
fun AuthView(initial: AuthMode, onClose: () -> Unit) {
    var mode by remember { mutableStateOf(initial) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val signup = mode == AuthMode.SIGNUP
    val valid = email.contains("@") && password.length >= 8 && (!signup || name.isNotBlank())

    DisposableEffect(Unit) { onDispose { GoogleSignIn.cancel() } }

    fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try {
                work()
                Sfx.play(Sfx.Cue.COIN); Haptic.success()
                onClose()
            } catch (e: ApiError) {
                error = if (e.offline) L("Impossible de joindre Loba. Vérifie ta connexion.", "Can’t reach Loba. Check your connection.") else e.message
                Haptic.error()
            } catch (_: GoogleSignIn.Cancelled) {
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = L("La connexion a échoué. Réessaie.", "Sign-in failed. Please try again.")
            } finally {
                busy = false
            }
        }
    }

    suspend fun submit() {
        val mail = email.trim().lowercase()
        if (signup) {
            // Progress made before signing up is kept with the new account.
            AppStore.signedUp(Api.signup(name.trim(), mail, password, if (AppStore.state.xp > 0) AppStore.state.json else null))
        } else {
            AppStore.signedInWith(Api.login(mail, password))
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LobaView(if (signup) Mood.HAPPY else Mood.WAVE, 92.dp)
            Bubble(Modifier.padding(bottom = 26.dp)) {
                Text(if (signup) L("Enchanté !", "Nice to meet you!") else L("Te revoilà !", "Welcome back!"), style = textFont(16, W.BOLD), color = Ink)
            }
        }
        Text(if (signup) L("Crée ton compte", "Create your account") else L("Connexion", "Sign in"), style = displayFont(30), color = Ink)
        Text(
            if (signup) L("Ta progression te suit sur tous tes appareils.", "Your progress follows you on every device.") else L("Reprends là où tu t’es arrêté.", "Pick up where you left off."),
            style = textFont(16), color = Ink2,
        )

        if (AppStore.googleAvailable) {
            ChunkyButton(
                L("Continuer avec Google", "Continue with Google"), kind = Chunky.OUTLINE, enabled = !busy,
                leading = {
                    Box(Modifier.size(22.dp).background(if (busy) Muted else Sky, CircleShape), contentAlignment = Alignment.Center) {
                        Text("G", style = textFont(14, W.BLACK), color = Color.White)
                    }
                },
            ) { run { GoogleSignIn.signIn(context) } }
        }

        if (signup) {
            Field(L("Prénom", "First name")) {
                LobaTextField(name, { name = it }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Next))
            }
        }
        Field("Email") {
            LobaTextField(email, { email = it }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        }
        Field(L("Mot de passe", "Password")) {
            LobaTextField(
                password, { password = it }, placeholder = if (signup) L("8 caractères ou plus", "8 characters or more") else "", password = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
        }
        if (error.isNotEmpty()) {
            Text(error, style = textFont(15, W.BOLD), color = CoralShade, modifier = Modifier.fillMaxWidth().background(CoralTint, RoundedCornerShape(14.dp)).padding(12.dp))
        }
        ChunkyButton(
            if (busy) "…" else if (signup) L("Créer mon compte", "Create my account") else L("Me connecter", "Sign in"),
            enabled = valid && !busy,
        ) { run { submit() } }

        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).tappable { error = ""; mode = if (signup) AuthMode.LOGIN else AuthMode.SIGNUP }, contentAlignment = Alignment.Center) {
            Text(
                if (signup) L("J’ai déjà un compte", "I already have an account") else L("Nouveau ? Créer un compte", "New here? Create an account"),
                style = textFont(15, W.HEAVY), color = Sky,
            )
        }

        if (signup) {
            Text(
                L("En créant un compte, tu acceptes notre politique de confidentialité.", "By creating an account you accept our privacy policy."),
                style = textFont(13), color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).tappable { links.openUri(Api.url("/privacy.html")) },
            )
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = textFont(15, W.BOLD), color = Ink)
        content()
    }
}

/**
 * Google sign-in happens in a browser tab; the server then hands a one-time code back to the app at loba://auth,
 * which MainActivity passes to `returned`.
 */
object GoogleSignIn {
    /** The learner closed the browser, or Google reported a cancellation. Silent. */
    class Cancelled : Exception()

    private var pending: CompletableDeferred<String>? = null

    fun returned(url: String) { pending?.complete(url) }

    /** The app is in front again. If no code came with it, the learner closed the browser. */
    fun resumed() {
        val waiting = pending ?: return
        MainScope().launch {
            delay(800)
            if (pending === waiting) waiting.completeExceptionally(Cancelled())
        }
    }

    fun cancel() { pending?.completeExceptionally(Cancelled()) }

    /** Throws ApiError with a message for the learner, or Cancelled. */
    suspend fun signIn(context: Context) {
        if (pending != null) throw Cancelled()
        val request = GoogleAuthRequest()
        val waiting = CompletableDeferred<String>()
        pending = waiting
        val callback = try {
            try {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(request.startURL(Api.origin)))
            } catch (_: Exception) {
                throw ApiError(400, L("Impossible d’ouvrir la connexion Google. [GOOGLE-START]", "Could not open Google sign-in. [GOOGLE-START]"))
            }
            waiting.await()
        } finally {
            // Retained until it completes, including cancellation and failed starts.
            if (pending === waiting) pending = null
        }
        val code = try {
            request.code(callback)
        } catch (e: GoogleAuthRequest.Failure.InvalidCallback) {
            throw ApiError(400, L("Le lien de retour de connexion est invalide. Réessaie. [GOOGLE-CALLBACK-${e.issue.raw}]", "The sign-in return link is invalid. Try again. [GOOGLE-CALLBACK-${e.issue.raw}]"))
        } catch (_: GoogleAuthRequest.Failure.InvalidState) {
            throw ApiError(400, L("Cette tentative de connexion a expiré ou ne correspond pas. Réessaie. [GOOGLE-STATE]", "This sign-in attempt expired or did not match. Try again. [GOOGLE-STATE]"))
        } catch (_: GoogleAuthRequest.Failure.InvalidCode) {
            throw ApiError(400, L("Le code de connexion reçu est invalide. Réessaie. [GOOGLE-CODE]", "The received sign-in code is invalid. Try again. [GOOGLE-CODE]"))
        } catch (e: GoogleAuthRequest.Failure.Provider) {
            if (e.reason == "google_cancelled") throw Cancelled()
            throw ApiError(
                400,
                if (e.reason == "google_existing") L("Un compte existe déjà avec cette adresse. Connecte-toi avec ton mot de passe, puis relie Google depuis le site.", "An account already exists with this email. Sign in with your password, then link Google from the website.")
                else L("La connexion Google a échoué. Réessaie.", "Google sign-in failed. Please try again."),
            )
        }
        AppStore.signedInWith(Api.redeemGoogle(code, request.verifier))
    }
}

// MARK: - First run: language and daily goal

@Composable
fun OnboardingView() {
    var step by remember { mutableIntStateOf(0) }
    var lang by remember {
        mutableStateOf(if (AppStore.catalog.curriculum[AppStore.state.lang] != null) AppStore.state.lang else AppStore.catalog.languages.firstOrNull()?.code ?: "")
    }
    var goal by remember { mutableIntStateOf(AppStore.state.goal) }
    val ui = AppStore.state.ui

    Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step == 1) {
                Box(Modifier.size(44.dp).tappable { step = 0 }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ChevronLeft, L("Retour", "Back"), Modifier.size(28.dp), tint = Muted)
                }
            }
            Meter(if (step == 0) 0.5 else 1.0, Modifier.weight(1f), color = Sky, height = 14.dp)
            UiSwitch()
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LobaView(if (step == 0) Mood.WAVE else Mood.THINK, 96.dp)
                Bubble(Modifier.padding(bottom = 24.dp)) {
                    Text(
                        if (step == 0) L("Quelle langue veux-tu apprendre ?", "Which language do you want to learn?") else L("Combien veux-tu apprendre par jour ?", "How much do you want to learn each day?"),
                        style = textFont(17, W.BOLD), color = Ink,
                    )
                }
            }
            if (step == 0) {
                AppStore.catalog.languages.forEachIndexed { i, l ->
                    Row(
                        Modifier.fillMaxWidth().tappable { Sfx.play(Sfx.Cue.TILE, i); lang = l.code }.card(selected = lang == l.code).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LangBadge(l.code, hexColor(l.color))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(l.name(ui).replaceFirstChar { it.uppercase() }, style = textFont(19, W.HEAVY), color = Ink)
                            Text(l.region(ui), style = textFont(14), color = Muted)
                        }
                    }
                }
            } else {
                val labels = listOf(L("Tranquille", "Easy"), L("Régulier", "Regular"), L("Sérieux", "Serious"), L("Intense", "Intense"))
                Game.goals.forEachIndexed { i, g ->
                    Row(
                        Modifier.fillMaxWidth().tappable { Sfx.play(Sfx.Cue.TILE, i); goal = g }.card(selected = goal == g).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Art("bolt", 28.dp)
                        Text("$g XP", style = textFont(19, W.HEAVY), color = Ink)
                        Spacer(Modifier.weight(1f))
                        Text(labels[i], style = textFont(15), color = Muted)
                    }
                }
            }
        }
        ChunkyButton(if (step == 0) L("Continuer", "Continue") else L("C’est parti !", "Let’s go!"), Modifier.padding(20.dp), enabled = lang.isNotEmpty()) {
            if (step == 0) step = 1 else { Sfx.play(Sfx.Cue.STREAK); AppStore.finishOnboarding(lang, goal) }
        }
    }
}
