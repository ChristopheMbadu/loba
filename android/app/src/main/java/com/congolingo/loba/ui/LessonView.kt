package com.congolingo.loba.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.Exercise
import com.congolingo.loba.core.ExerciseKind
import com.congolingo.loba.core.Game
import com.congolingo.loba.core.Grade
import com.congolingo.loba.core.Grading
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.L
import com.congolingo.loba.core.LessonBuilder
import com.congolingo.loba.core.LessonSession
import com.congolingo.loba.core.LessonUnit
import com.congolingo.loba.core.PracticeMode
import com.congolingo.loba.core.Reminder
import com.congolingo.loba.core.Rules
import com.congolingo.loba.core.SessionStatus
import com.congolingo.loba.core.Sfx
import com.congolingo.loba.core.Voice
import com.congolingo.loba.core.Word
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt

sealed class LessonMode {
    data class Lesson(val unit: LessonUnit, val index: Int) : LessonMode()
    data class Practice(val mode: PracticeMode) : LessonMode()

    val isLesson: Boolean get() = this is Lesson
}

/** What the learner has entered for the exercise on screen. */
private sealed class Answer {
    data class Choice(val word: Word) : Answer()
    data class Tiles(val tiles: List<String>) : Answer()
    data class Text(val text: String) : Answer()
    data object Matched : Answer()
}

/** SwiftUI's `.spring(duration:bounce:)`. */
private fun <T> bouncy(duration: Double, bounce: Double = 0.0): SpringSpec<T> {
    val w = 2 * PI / duration
    return spring(dampingRatio = (1 - bounce).toFloat(), stiffness = (w * w).toFloat())
}

/** The exercises of a lesson or a practice session, from the learner's progress. */
private fun begin(mode: LessonMode): List<Exercise> {
    val s = AppStore.state
    val units = AppStore.units
    val pool = Rules.unlockedWords(s, units)
    val seen = s.words.keys.filter { it.startsWith("${s.lang}:") }.map { it.drop(s.lang.length + 1) }.toSet()
    return when (mode) {
        is LessonMode.Lesson -> LessonBuilder.lesson(mode.unit, mode.index, pool, s.voice, seen)
        is LessonMode.Practice -> {
            var words = Rules.reviewWords(s, s.lang, pool, Game.now())
            // Nothing due yet: practise the words met so far, weakest first.
            if (words.size < 4) words = pool.filter { it.term in seen }.shuffled()
            LessonBuilder.practice(if (mode.mode == PracticeMode.LISTEN) pool.shuffled() else words, pool, mode.mode, s.voice)
        }
    }
}

@Composable
fun LessonView(mode: LessonMode, onClose: () -> Unit) {
    var session by remember { mutableStateOf(LessonSession(begin(mode))) }
    var answer by remember { mutableStateOf<Answer?>(null) }
    var reward by remember { mutableStateOf<Rules.Reward?>(null) }
    var confirmQuit by remember { mutableStateOf(false) }
    var outOfHearts by remember { mutableStateOf(false) }
    var sparks by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun check() {
        val e = session.current
        val a = answer
        if (session.status != SessionStatus.ANSWERING || e == null || a == null) return
        val k = e.kind
        val grade = when {
            k is ExerciseKind.Meaning && a is Answer.Choice -> if (a.word == k.word) Grade.CORRECT else Grade.WRONG
            k is ExerciseKind.Term && a is Answer.Choice -> if (a.word == k.word) Grade.CORRECT else Grade.WRONG
            k is ExerciseKind.Listen && a is Answer.Choice -> if (a.word == k.word) Grade.CORRECT else Grade.WRONG
            k is ExerciseKind.Build && a is Answer.Tiles -> Grading.tiles(a.tiles, k.sentence.answer)
            k is ExerciseKind.Type && a is Answer.Text -> Grading.typed(a.text, k.word.term)
            k is ExerciseKind.Match && a is Answer.Matched -> Grade.CORRECT
            else -> return
        }
        session = session.grade(grade)
        e.word?.let { AppStore.recordWord(it.term, grade != Grade.WRONG) }
        if (grade == Grade.WRONG) {
            Sfx.play(Sfx.Cue.WRONG); Haptic.error()
            scope.launch {
                repeat(2) {
                    shake.animateTo(-8f, tween(60))
                    shake.animateTo(0f, tween(60))
                }
            }
            if (mode.isLesson) AppStore.loseHeart()
        } else {
            Sfx.play(Sfx.Cue.CORRECT, session.combo); Haptic.success()
            sparks += 1
            if (k !is ExerciseKind.Listen) e.word?.let { Voice.say(it) }
        }
    }

    fun advance() {
        if (mode.isLesson && AppStore.state.hearts == 0 && session.status == SessionStatus.WRONG) { outOfHearts = true; return }
        answer = null
        session = session.next()
        if (!session.finished) { Sfx.play(Sfx.Cue.WHOOSH); return }
        val lesson = mode as? LessonMode.Lesson
        val result = Rules.SessionResult(
            kind = if (mode.isLesson) Rules.SessionKind.LESSON else Rules.SessionKind.PRACTICE,
            unitId = lesson?.unit?.id, lessonIndex = lesson?.index, mistakes = session.mistakes, bestCombo = session.bestCombo,
        )
        val earned = AppStore.complete(result)
        Sfx.play(Sfx.Cue.COMPLETE)
        reward = earned
    }

    fun finish() {
        onClose()
        // Once the celebration is over, offer the evening reminder (asked only the first time).
        Reminder.afterLesson()
    }

    LaunchedEffect(Unit) { if (session.queue.isEmpty()) onClose() }

    // `autoplay` answers every exercise correctly (and `autoplay wrong` misses the second one), for screenshots.
    LaunchedEffect(Unit) {
        val style = DebugFlags.autoplay ?: return@LaunchedEffect
        var n = 0
        while (reward == null) {
            val e = session.current ?: break
            delay(1100)
            n += 1
            fun pick(w: Word, o: List<Word>): Answer = Answer.Choice(if (style == "wrong" && n == 2) o.firstOrNull { it != w } ?: w else w)
            answer = when (val k = e.kind) {
                is ExerciseKind.Meaning -> pick(k.word, k.options)
                is ExerciseKind.Term -> pick(k.word, k.options)
                is ExerciseKind.Listen -> pick(k.word, k.options)
                is ExerciseKind.Build -> Answer.Tiles(Grading.toTiles(k.sentence.answer))
                is ExerciseKind.Type -> Answer.Text(k.word.term)
                is ExerciseKind.Match -> Answer.Matched
            }
            if (style == "hold") return@LaunchedEffect
            delay(500)
            check()
            delay(1300)
            advance()
        }
    }

    // The back button is the close button; on the reward screen it is "Continue".
    BackHandler { if (reward != null) finish() else confirmQuit = true }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        val exercise = session.current
        if (reward == null && exercise != null) {
            Column(Modifier.fillMaxSize().statusBarsPadding().graphicsLayer { translationX = shake.value.dp.toPx() }) {
                Header(session, mode.isLesson) { confirmQuit = true }
                AnimatedContent(
                    targetState = exercise,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (slideInHorizontally(bouncy(0.35)) { it } + fadeIn(bouncy(0.35))) togetherWith
                            (slideOutHorizontally(bouncy(0.35)) { -it } + fadeOut(bouncy(0.35)))
                    },
                    contentKey = { it.id },
                    label = "exercise",
                ) { shown ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).readable().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)) {
                        val live = session.current?.id == shown.id
                        ExerciseView(
                            exercise = shown, answer = if (live) answer else null,
                            onAnswer = { if (session.current?.id == shown.id) answer = it },
                            locked = !live || session.status != SessionStatus.ANSWERING,
                            submit = { check() },
                        )
                    }
                }
                Footer(session, exercise, answer, onCheck = { check() }, onContinue = { advance() })
            }
        }
        Burst(trigger = sparks, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 60.dp).fillMaxWidth().height(420.dp))
        AnimatedVisibility(
            visible = reward != null,
            enter = fadeIn(bouncy(0.5)) + scaleIn(bouncy(0.5), initialScale = 0.96f),
            exit = fadeOut(),
        ) {
            reward?.let {
                CompleteView(reward = it, accuracy = session.accuracy, combo = session.bestCombo, practice = !mode.isLesson, done = { finish() })
            }
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            containerColor = Color.White,
            title = { Text(L("Attends, ne pars pas !", "Wait, don’t go!"), style = displayFont(21), color = Ink) },
            text = { Text(L("Ta progression dans cette leçon sera perdue.", "Your progress in this lesson will be lost."), style = textFont(16), color = Ink2) },
            confirmButton = {
                TextButton(onClick = { confirmQuit = false }) { Text(L("Continuer à apprendre", "Keep learning"), style = textFont(16, W.HEAVY), color = Sky) }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false; onClose() }) { Text(L("Quitter la leçon", "Leave the lesson"), style = textFont(16, W.HEAVY), color = Coral) }
            },
        )
    }
    if (outOfHearts) {
        // Like the iOS alert, it only goes away with one of its buttons.
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = Color.White,
            title = { Text(L("Plus de cœurs", "Out of hearts"), style = displayFont(21), color = Ink) },
            text = { Text(L("Un cœur revient toutes les 30 minutes.", "A heart comes back every 30 minutes."), style = textFont(16), color = Ink2) },
            confirmButton = {
                if (Rules.canBuy(AppStore.state, Rules.ShopItem.REFILL, Game.now())) {
                    TextButton(onClick = { outOfHearts = false; AppStore.buy(Rules.ShopItem.REFILL); Sfx.play(Sfx.Cue.HEART); advance() }) {
                        Text(L("Recharger (50 gemmes)", "Refill (50 gems)"), style = textFont(16, W.HEAVY), color = Sky)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { outOfHearts = false; onClose() }) { Text(L("Quitter", "Leave"), style = textFont(16, W.HEAVY), color = Ink2) }
            },
        )
    }
}

@Composable
private fun Header(session: LessonSession, hearts: Boolean, close: () -> Unit) {
    Box(Modifier.fillMaxWidth().zIndex(1f)) {
        // The close button is 44dp around a 24dp cross; the paddings keep the cross where iOS draws it.
        Row(Modifier.readable().padding(start = 10.dp, end = 20.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).tappable(onClick = close), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, L("Quitter la leçon", "Leave the lesson"), Modifier.size(24.dp), tint = Muted)
            }
            Spacer(Modifier.width(4.dp))
            Meter(session.progress, Modifier.weight(1f), color = if (session.combo >= 3) Sun else Leaf)
            if (hearts) {
                Spacer(Modifier.width(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art("heart", 26.dp)
                    AnimatedContent(
                        targetState = AppStore.state.hearts,
                        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                        label = "hearts",
                    ) { n -> Text("$n", style = textFont(18, W.HEAVY), color = Coral) }
                }
            }
        }
        // Keeps the last count on screen while the label shrinks away.
        val last = remember { intArrayOf(3) }
        if (session.combo >= 3) last[0] = session.combo
        AnimatedVisibility(
            visible = session.combo >= 3,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp).offset(y = 10.dp),
            enter = scaleIn(bouncy(0.3, 0.5)) + fadeIn(bouncy(0.3)),
            exit = scaleOut(bouncy(0.3)) + fadeOut(bouncy(0.3)),
        ) {
            Text("${last[0]} ${L("d’affilée", "in a row")} 🔥", style = textFont(13, W.HEAVY), color = SunShade)
        }
    }
}

@Composable
private fun Footer(session: LessonSession, exercise: Exercise, answer: Answer?, onCheck: () -> Unit, onContinue: () -> Unit) {
    val status = session.status
    val answering = status == SessionStatus.ANSWERING
    val wrong = status == SessionStatus.WRONG
    val background by animateColorAsState(if (answering) Color.White else if (wrong) CoralTint else LeafTint, bouncy(0.3), label = "footer")
    val ready = when (answer) {
        is Answer.Choice, Answer.Matched -> true
        is Answer.Tiles -> answer.tiles.isNotEmpty()
        is Answer.Text -> answer.text.isNotBlank()
        null -> false
    }
    // The colour runs under the navigation bar; the buttons stay above it and above the keyboard.
    Column(
        Modifier.fillMaxWidth().background(background)
            .drawBehind { if (answering) drawRect(Line, size = Size(size.width, 2.dp.toPx())) }
            .navigationBarsPadding().imePadding().readable()
            .animateContentSize(bouncy(0.3, 0.3))
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!answering) {
            val ink = if (wrong) CoralShade else LeafShade
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                LobaView(mood = if (wrong) Mood.SAD else Mood.CHEER, size = 64.dp, tappable = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (wrong) L("Pas tout à fait", "Not quite") else if (status == SessionStatus.TYPO) L("Presque parfait !", "Almost perfect!") else cheer(session.solved),
                        style = displayFont(21), color = ink,
                    )
                    val solution = solution(exercise)
                    if (status != SessionStatus.CORRECT && solution != null) {
                        Text("${L("Réponse", "Answer")} : $solution", style = textFont(16, W.BOLD), color = ink)
                    }
                }
            }
        }
        if (answering) {
            if (exercise.kind !is ExerciseKind.Match) ChunkyButton(L("Vérifier", "Check"), kind = Chunky.GREEN, enabled = ready, onClick = onCheck)
        } else {
            ChunkyButton(L("Continuer", "Continue"), kind = if (wrong) Chunky.DANGER else Chunky.GREEN, onClick = onContinue)
        }
    }
}

private fun cheer(solved: Int): String {
    val own = AppStore.language?.cheer ?: ""
    return listOf(own.ifEmpty { L("Bravo !", "Well done!") }, L("Bien joué !", "Nicely done!"), L("Excellent !", "Excellent!"))[solved % 3]
}

private fun solution(e: Exercise): String? = when (val k = e.kind) {
    is ExerciseKind.Meaning -> k.word.meaning(AppStore.state.ui)
    is ExerciseKind.Term -> k.word.term
    is ExerciseKind.Listen -> k.word.term
    is ExerciseKind.Type -> k.word.term
    is ExerciseKind.Build -> k.sentence.answer
    is ExerciseKind.Match -> null
}

// MARK: - Exercises

@Composable
private fun ExerciseView(exercise: Exercise, answer: Answer?, onAnswer: (Answer) -> Unit, locked: Boolean, submit: () -> Unit) {
    val ui = AppStore.state.ui
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (exercise.retry) Tag(Icons.Rounded.Refresh, L("Erreur précédente", "Previous mistake"), SunShade, 0.0)
        when (val k = exercise.kind) {
            is ExerciseKind.Meaning -> {
                if (k.isNew && !exercise.retry) Tag(Icons.Rounded.AutoAwesome, L("Nouveau mot", "New word"), Plum, 1.0)
                Title(L("Que veut dire ce mot ?", "What does this mean?"))
                Prompt(k.word.term, k.word)
                Choices(k.options, k.word, answer, onAnswer, locked) { it.meaning(ui) }
            }
            is ExerciseKind.Term -> {
                Title(L("Comment dit-on…", "How do you say…"))
                Prompt(k.word.meaning(ui), null)
                Choices(k.options, k.word, answer, onAnswer, locked) { it.term }
            }
            is ExerciseKind.Listen -> {
                Title(L("Qu’entends-tu ?", "What do you hear?"))
                LaunchedEffect(k.word) { Voice.say(k.word) }
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(110.dp)
                            .drawBehind {
                                val r = CornerRadius(28.dp.toPx())
                                drawRoundRect(SkyShade, Offset(0f, 6.dp.toPx()), size, r)
                                drawRoundRect(Sky, Offset.Zero, size, r)
                            }
                            .tappable { Voice.say(k.word) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, L("Écouter", "Listen"), Modifier.size(54.dp), tint = Color.White) }
                }
                Choices(k.options, k.word, answer, onAnswer, locked) { it.term }
            }
            is ExerciseKind.Match -> {
                Title(L("Relie les paires", "Match the pairs"))
                MatchBoard(k.words) { onAnswer(Answer.Matched); submit() }
            }
            is ExerciseKind.Build -> {
                Title(L("Traduis cette phrase", "Translate this sentence"))
                Prompt(k.sentence.prompt(ui), null)
                TileBoard(k.tiles, locked) { onAnswer(Answer.Tiles(it)) }
            }
            is ExerciseKind.Type -> {
                val name = AppStore.language?.name(ui) ?: ""
                Title(L("Écris en $name", "Type it in $name"))
                Prompt(k.word.meaning(ui), null)
                TypeField(locked, submit) { onAnswer(Answer.Text(it)) }
            }
        }
    }
}

@Composable
private fun Tag(icon: ImageVector, text: String, color: Color, tracking: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(text.uppercase(), style = textFont(13, W.HEAVY), color = color, letterSpacing = tracking.sp)
    }
}

@Composable
private fun Title(text: String) = Text(text, style = displayFont(25), color = Ink)

@Composable
private fun Prompt(text: String, word: Word?) {
    LaunchedEffect(word) { word?.let { Voice.say(it) } }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        LobaView(mood = Mood.IDLE, size = 84.dp)
        Bubble(Modifier.weight(1f, fill = false).tappable(enabled = word != null) { word?.let { Voice.say(it) } }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (word != null && word.audio != null) Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(22.dp), tint = Sky)
                Text(text, style = textFont(20, W.HEAVY), color = Ink)
            }
        }
    }
}

@Composable
private fun Choices(options: List<Word>, correct: Word, answer: Answer?, onAnswer: (Answer) -> Unit, locked: Boolean, label: (Word) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, option ->
            key(option) {
                val picked = answer == Answer.Choice(option)
                val tint = if (!locked) Sky else if (option == correct) Leaf else if (picked) Coral else Sky
                val scale by animateFloatAsState(if (picked) 1.02f else 1f, bouncy(0.2, 0.5), label = "pick")
                Row(
                    Modifier.fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .card(selected = picked || (locked && option == correct), tint = tint)
                        .tappable(enabled = !locked) {
                            Sfx.play(Sfx.Cue.TILE, i); Haptic.tap()
                            onAnswer(Answer.Choice(option))
                        }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).border(2.dp, if (picked) tint else Line, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = textFont(14, W.HEAVY), color = if (picked) tint else Muted)
                    }
                    Text(label(option), style = textFont(18, W.BOLD), color = Ink, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MatchBoard(pairs: List<Word>, done: () -> Unit) {
    val ui = AppStore.state.ui
    val meanings = remember(pairs) { pairs.shuffled() }
    var left by remember { mutableStateOf<Word?>(null) }
    var right by remember { mutableStateOf<Word?>(null) }
    var matched by remember { mutableStateOf(setOf<Word>()) }
    var wrong by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun resolve() {
        val l = left ?: return
        val r = right ?: return
        if (l == r) {
            matched = matched + l
            left = null; right = null
            Sfx.play(Sfx.Cue.POP); Haptic.success()
            if (matched.size == pairs.size) scope.launch { delay(350); done() }
        } else {
            wrong = true
            Haptic.error()
            scope.launch { delay(450); wrong = false; left = null; right = null }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        MatchColumn(pairs, left, matched, wrong, { it.term }) { left = it; Voice.say(it); resolve() }
        MatchColumn(meanings, right, matched, wrong, { it.meaning(ui) }) { right = it; resolve() }
    }
}

@Composable
private fun RowScope.MatchColumn(words: List<Word>, selected: Word?, matched: Set<Word>, wrong: Boolean, label: (Word) -> String, tap: (Word) -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        words.forEachIndexed { i, w ->
            key(w) {
                val isMatched = w in matched
                val picked = selected == w
                Box(
                    Modifier.fillMaxWidth()
                        .alpha(if (isMatched) 0.55f else 1f)
                        .card(selected = picked || isMatched, tint = if (isMatched) Leaf else if (wrong && picked) Coral else Sky)
                        .tappable(enabled = !isMatched) { Sfx.play(Sfx.Cue.TILE, i); Haptic.tap(); tap(w) }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .heightIn(min = 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(w), style = textFont(16, W.BOLD), color = if (isMatched) Muted else Ink, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileBoard(tiles: List<String>, locked: Boolean, changed: (List<String>) -> Unit) {
    // Indexes into `tiles`, in the order the learner placed them.
    var placed by remember { mutableStateOf(listOf<Int>()) }
    // Tiles pop in where they land (iOS flies them across with matchedGeometryEffect).
    var touched by remember { mutableStateOf(false) }

    fun tap(i: Int, isPlaced: Boolean) {
        Haptic.tap()
        touched = true
        if (isPlaced) { placed = placed - i; Sfx.play(Sfx.Cue.TAP) } else { placed = placed + i; Sfx.play(Sfx.Cue.TILE, placed.size - 1) }
        changed(placed.map { tiles[it] })
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        FlowRow(
            Modifier.fillMaxWidth()
                .drawBehind { drawRect(Line, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }
                .padding(bottom = 8.dp).heightIn(min = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (i in placed) key(i) { Tile(tiles[i], locked, touched) { tap(i, true) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in tiles.indices) key(i) {
                if (i in placed) {
                    Text(
                        tiles[i], style = textFont(17, W.BOLD), color = Color.Transparent,
                        modifier = Modifier.background(Line, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                } else Tile(tiles[i], locked, touched) { tap(i, false) }
            }
        }
    }
}

@Composable
private fun Tile(text: String, locked: Boolean, pop: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(if (pop && !Motion.still) 0.7f else 1f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, bouncy(0.3, 0.35)) }
    Text(
        text, style = textFont(17, W.BOLD), color = Ink,
        modifier = Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .drawBehind {
                val r = 13.dp.toPx()
                val w = 2.dp.toPx()
                drawRoundRect(Line, Offset(0f, 3.dp.toPx()), size, CornerRadius(r))
                drawRoundRect(Color.White, Offset.Zero, size, CornerRadius(r))
                drawRoundRect(Line, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(r - w / 2), style = Stroke(w))
            }
            .tappable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

@Composable
private fun TypeField(locked: Boolean, submit: () -> Unit, changed: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LobaTextField(
        value = text, onValueChange = { text = it; changed(it) },
        modifier = Modifier.focusRequester(focus),
        placeholder = L("Écris ici", "Type here"), enabled = !locked,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { defaultKeyboardAction(ImeAction.Done); submit() }),
    )
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: IllegalStateException) {} }
}

// MARK: - Lesson complete

@Composable
private fun CompleteView(reward: Rules.Reward, accuracy: Int, combo: Int, practice: Boolean, done: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val pop by animateFloatAsState(if (shown) 1f else 0f, bouncy(0.7, 0.5), label = "shown")
    val xp = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        Haptic.success()
        delay(150)
        shown = true
        delay(150)
        if (Motion.still) xp.snapTo(reward.xp.toFloat()) else xp.animateTo(reward.xp.toFloat(), tween(900, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        if (reward.streakExtended) { delay(1600); Sfx.play(Sfx.Cue.STREAK) }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().readable(560.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.requiredSize(340.dp)) {
                    val r = 170.dp.toPx()
                    drawCircle(Brush.radialGradient(0f to SunTint, 20f / 170f to SunTint, 1f to Color.White.copy(alpha = 0f), center = center, radius = r), r)
                }
                SunRays(Modifier.requiredSize(330.dp).graphicsLayer { alpha = (0.55f * pop).coerceIn(0f, 1f) })
                LobaView(mood = Mood.CHEER, size = 190.dp, modifier = Modifier.graphicsLayer { translationY = 40.dp.toPx() * (1f - pop) })
            }
            Text(
                if (reward.perfect) L("Sans faute !", "Flawless!") else if (practice) L("Pratique terminée !", "Practice done!") else L("Leçon terminée !", "Lesson complete!"),
                style = displayFont(34), color = Ink, textAlign = TextAlign.Center,
            )
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("bolt", "+${xp.value.roundToInt()}", "XP", SunShade, pop)
                Stat("target", "$accuracy%", L("Précision", "Accuracy"), LeafShade, pop)
                if (reward.gems > 0) Stat("gem", "+${reward.gems}", L("Gemmes", "Gems"), Sky, pop)
                else Stat("flame", "$combo", L("Série max", "Best combo"), Coral, pop)
            }
            if (reward.streakExtended) {
                val streak = Rules.visibleStreak(AppStore.state, AppStore.today)
                Row(
                    Modifier
                        .graphicsLayer { val s = 0.5f + 0.5f * pop; scaleX = s; scaleY = s; alpha = pop.coerceIn(0f, 1f) }
                        .background(SunTint, CircleShape).padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Art("flame", 34.dp)
                    Text(
                        if (streak == 1) L("Ta série commence : 1 jour !", "Your streak starts: 1 day!") else L("Série de $streak jours !", "$streak-day streak!"),
                        style = textFont(18, W.HEAVY), color = SunShade,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            ChunkyButton(L("Continuer", "Continue"), Modifier.padding(20.dp), kind = Chunky.GREEN, onClick = done)
        }
        Confetti(Modifier.fillMaxSize())
    }
}

@Composable
private fun RowScope.Stat(art: String, value: String, label: String, color: Color, pop: Float) {
    Column(
        Modifier.weight(1f)
            .graphicsLayer { val s = 0.6f + 0.4f * pop; scaleX = s; scaleY = s; alpha = pop.coerceIn(0f, 1f) }
            .card().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Art(art, 34.dp)
        Text(value, style = displayFont(24), color = color)
        Text(label.uppercase(), style = textFont(12, W.HEAVY), color = Muted)
    }
}
