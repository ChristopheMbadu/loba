package com.congolingo.loba.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.congolingo.loba.core.Api
import com.congolingo.loba.core.ApiError
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.Game
import com.congolingo.loba.core.Haptic
import com.congolingo.loba.core.L
import com.congolingo.loba.core.PracticeMode
import com.congolingo.loba.core.Reminder
import com.congolingo.loba.core.Rules
import com.congolingo.loba.core.Sfx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
private fun PageHead(title: String, text: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = displayFont(30), color = Ink)
        Text(text, style = textFont(16), color = Ink2)
    }
}

/** One of a page's big buttons: a card, greyed out while it can't be used. */
@Composable
private fun Tile(enabled: Boolean = true, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f).card().tappable(enabled, onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

@Composable
private fun TileCard(art: String, tint: Color, title: String, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Tile(enabled, onClick) {
        Box(Modifier.size(68.dp).background(tint, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Art(art, 40.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = textFont(19, W.HEAVY), color = Ink)
            Text(text, style = textFont(15), color = Muted)
        }
    }
}

// MARK: - Practice

@Composable
fun PracticeView() {
    var mode by remember { mutableStateOf<PracticeMode?>(null) }
    var story by remember { mutableStateOf(false) }
    var puzzle by remember { mutableStateOf(false) }
    var guessing by remember { mutableStateOf(false) }
    var phrasebook by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf(false) }

    val s = AppStore.state
    val pool = Rules.unlockedWords(s, AppStore.units)
    val due = Rules.reviewWords(s, s.lang, pool, Game.now()).size
    val seen = s.words.keys.any { it.startsWith("${s.lang}:") }
    val listen = s.voice && pool.count { it.audio != null } >= 4
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHead(L("Pratique", "Practice"), L("La pratique ne coûte jamais de cœurs. Les mots ratés reviennent plus souvent.", "Practice never costs hearts. Words you miss come back more often."))
        TileCard(
            "cards", LeafTint, L("Révision intelligente", "Smart review"),
            if (seen) L("$due mots à revoir aujourd’hui", "$due words to review today") else L("Termine une leçon pour débloquer la révision.", "Finish a lesson to unlock review."),
            enabled = seen,
        ) { mode = PracticeMode.REVIEW }
        TileCard(
            "headphones", Plum.copy(alpha = 0.14f), L("Écoute", "Listening"),
            if (listen) L("Reconnais les mots à l’oreille.", "Recognise words by ear.") else L("Pas encore assez d’enregistrements pour ces leçons.", "Not enough recordings for these lessons yet."),
            enabled = listen,
        ) { mode = PracticeMode.LISTEN }
        TileCard(
            "word", SkyTint, L("Mots mêlés", "Word hunt"),
            L("Trouve les mots cachés : chacun révèle son sens et une phrase.", "Find the hidden words: each reveals its meaning and a sentence."),
        ) { puzzle = true }
        TileCard(
            "cards", SunTint, L("Devine le mot", "Guess the word"),
            L("À deux, fais deviner les mots de tes leçons.", "Play with a partner and guess words from your lessons."),
        ) { guessing = true }
        TileCard(
            "book", LeafTint, L("Mon carnet", "My phrasebook"),
            L("Garde tes mots et tes phrases avec leur traduction.", "Save your words and sentences with their translations."),
        ) { phrasebook = true }
        Tile(onClick = { map = true }) {
            Box(Modifier.size(68.dp).background(LeafTint, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("🗺️", fontSize = 36.sp, color = Ink) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(L("Mboka na biso · La carte du Congo", "Mboka na biso · The map of Congo"), style = textFont(19, W.HEAVY), color = Ink)
                Text(L("Remets les 26 provinces à leur place et découvre le pays.", "Put the 26 provinces back in place and discover the country."), style = textFont(15), color = Muted)
            }
        }
        TileCard(
            "book", SunTint, L("Masapo · Contes du Congo", "Masapo · Tales of Congo"),
            L("Un conte illustré, avec des mots de ta langue.", "An illustrated folktale with words from your language."),
        ) { story = true }
    }

    mode?.let { m -> FullScreenCover(onDismiss = { mode = null }) { LessonView(LessonMode.Practice(m), onClose = { mode = null }) } }
    if (story) FullScreenCover(onDismiss = { story = false }) { StoryView(onClose = { story = false }) }
    if (puzzle) FullScreenCover(onDismiss = { puzzle = false }) { PuzzleHub(onClose = { puzzle = false }) }
    if (guessing) {
        FullScreenCover(onDismiss = { guessing = false }) {
            GuessWordGame(words = pool.filter { it.guessWord }, ui = s.ui, notebook = Notebook.key(), onClose = { guessing = false })
        }
    }
    if (phrasebook) Sheet(onDismiss = { phrasebook = false }) { PhrasebookView(key = Notebook.key(), onClose = { phrasebook = false }) }
    if (map) FullScreenCover(onDismiss = { map = false }) { MapGameView(onClose = { map = false }) }
}

// MARK: - Quests

@Composable
fun QuestsView() {
    val quests = Rules.dailyQuests(AppStore.state, AppStore.today)
    val art = mapOf("xp" to "bolt", "lessons" to "book", "combo" to "flame", "perfect" to "target")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHead(L("Quêtes du jour", "Daily quests"), L("De nouvelles quêtes chaque matin. Les gemmes s’échangent à la boutique.", "New quests every morning. Spend your gems in the shop."))
        for (q in quests) {
            val claimed = q.id.raw in AppStore.state.daily.claimed
            Column(
                Modifier.fillMaxWidth().card(selected = q.done && !claimed, tint = Sun).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(art.getValue(q.id.raw), 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(questTitle(q), style = textFont(17, W.HEAVY), color = Ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Meter(q.value.toDouble() / q.goal.toDouble(), Modifier.weight(1f), color = if (q.done) Leaf else Sun, height = 14.dp)
                            Text("${min(q.value, q.goal)}/${q.goal}", style = textFont(13, W.HEAVY), color = Muted)
                        }
                    }
                }
                if (q.done) {
                    ChunkyButton(
                        if (claimed) L("Récupéré", "Claimed") else "+${q.reward} " + L("gemmes", "gems"),
                        kind = Chunky.SKY, small = true, enabled = !claimed,
                        leading = { Icon(if (claimed) Icons.Rounded.Check else Icons.Rounded.Diamond, null, Modifier.size(18.dp), tint = if (claimed) Muted else Color.White) },
                    ) {
                        AppStore.claim(q.id)
                        Sfx.play(Sfx.Cue.COIN); Haptic.success()
                    }
                }
            }
        }
    }
}

private fun questTitle(q: Rules.Quest): String = when (q.id) {
    Rules.QuestId.XP -> L("Gagne ${q.goal} XP", "Earn ${q.goal} XP")
    Rules.QuestId.LESSONS -> L("Termine ${q.goal} leçons", "Finish ${q.goal} lessons")
    Rules.QuestId.COMBO -> L("${q.goal} bonnes réponses d’affilée", "${q.goal} right answers in a row")
    Rules.QuestId.PERFECT -> L("Une leçon sans faute", "One flawless lesson")
}

// MARK: - Shop

@Composable
fun ShopView() {
    val s = AppStore.state
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHead(L("Boutique", "Shop"), L("Dépense tes gemmes pour protéger ta série ou aller plus vite.", "Spend gems to protect your streak or go faster."))
        ShopRow(Rules.ShopItem.REFILL, "heart", CoralTint, L("Recharge de cœurs", "Heart refill"), L("Retrouve tes 5 cœurs tout de suite.", "Get all 5 hearts back right away."))
        ShopRow(
            Rules.ShopItem.FREEZE, "freeze", SkyTint, L("Gel de série", "Streak freeze"),
            L("Protège ta série si tu rates un jour. Tu en as ${s.freezes} sur ${Game.maxFreezes}.", "Protects your streak if you miss a day. You have ${s.freezes} of ${Game.maxFreezes}."),
        )
        ShopRow(
            Rules.ShopItem.DOUBLE_XP, "bolt", SunTint, L("Double XP", "Double XP"),
            if (Game.now() < s.doubleXpUntil) L("Actif en ce moment !", "Active right now!") else L("Deux fois plus d’XP pendant 15 minutes.", "Twice the XP for 15 minutes."),
        )
    }
}

@Composable
private fun ShopRow(item: Rules.ShopItem, art: String, tint: Color, title: String, text: String) {
    val can = Rules.canBuy(AppStore.state, item, Game.now())
    Row(
        Modifier.fillMaxWidth().card().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).background(tint, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Art(art, 40.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = textFont(18, W.HEAVY), color = Ink)
            Text(text, style = textFont(14), color = Muted)
        }
        ChunkyButton("${item.price}", kind = Chunky.OUTLINE, small = true, enabled = can, leading = { Art("gem", 18.dp) }) {
            AppStore.buy(item)
            Sfx.play(if (item == Rules.ShopItem.REFILL) Sfx.Cue.HEART else Sfx.Cue.COIN); Haptic.success()
        }
    }
}

// MARK: - Profile

@Composable
fun ProfileView() {
    var reminder by remember { mutableStateOf(Reminder.isOn) }
    var editing by remember { mutableStateOf(false) }
    var auth by remember { mutableStateOf<AuthMode?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var accountError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val links = LocalUriHandler.current

    val s = AppStore.state
    val ui = s.ui
    val learner = AppStore.learner
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            Modifier.fillMaxWidth().tappable { editing = true },
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(84.dp).background(SkyTint, CircleShape), contentAlignment = Alignment.Center) { Text(s.avatar, fontSize = 46.sp, color = Ink) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(s.name.ifEmpty { learner?.name ?: L("Apprenant", "Learner") }, style = displayFont(26), color = Ink)
                Text(L("Modifier le profil", "Edit profile"), style = textFont(14, W.BOLD), color = Sky)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell("flame", "${Rules.visibleStreak(s, AppStore.today)}", L("Jours de série", "Day streak"))
                StatCell("bolt", "${s.xp}", "XP")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell("word", "${Rules.wordsLearned(s, s.lang)}", L("Mots appris", "Words learned"))
                StatCell("trophy", "${s.bestStreak}", L("Meilleure série", "Best streak"))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(L("Cette semaine", "This week"), style = displayFont(22), color = Ink)
            Box(Modifier.fillMaxWidth().card().padding(vertical = 12.dp, horizontal = 6.dp)) { WeekStrip(Modifier.fillMaxWidth()) }
        }

        Column {
            SectionTitle(L("Réglages", "Settings"))
            SettingRow(L("J’apprends", "I’m learning")) {
                PickerMenu(s.lang, AppStore.catalog.languages.map { it.code to capitalized(it.name(ui)) }) { AppStore.setLang(it) }
            }
            SettingRow(L("Langue de l’interface", "Interface language")) { UiSwitch() }
            SettingRow(L("Objectif du jour", "Daily goal")) {
                PickerMenu(s.goal, Game.goals.map { it to "$it XP" }) { AppStore.setGoal(it) }
            }
            ToggleRow(L("Effets sonores", "Sound effects"), s.sound) { AppStore.toggleSound() }
            ToggleRow(L("Enregistrements audio", "Audio recordings"), s.voice) { AppStore.toggleVoice() }
            ToggleRow(L("Rappel du soir (19 h)", "Evening reminder (7 pm)"), reminder) { scope.launch { reminder = Reminder.set(!reminder) } }
        }

        Column {
            SectionTitle(L("Compte", "Account"))
            if (learner != null) {
                SettingRow(learner.name, sub = learner.email) {
                    ChunkyButton(L("Déconnexion", "Sign out"), kind = Chunky.OUTLINE, small = true) { confirmSignOut = true }
                }
            } else {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        L("Ta progression est gardée sur ce téléphone. Crée un compte pour la retrouver partout.", "Your progress is kept on this phone. Create an account to find it everywhere."),
                        style = textFont(15), color = Ink2,
                    )
                    ChunkyButton(L("Créer un compte", "Create an account"), kind = Chunky.GREEN) { auth = AuthMode.SIGNUP }
                    ChunkyButton(L("J’ai déjà un compte", "I already have an account"), kind = Chunky.OUTLINE) { auth = AuthMode.LOGIN }
                }
            }
        }

        if (accountError.isNotEmpty()) Text(accountError, style = textFont(14, W.BOLD), color = CoralShade)
        Text(
            L("Politique de confidentialité", "Privacy policy"), style = textFont(15, W.BOLD), color = Sky,
            modifier = Modifier.tappable { try { links.openUri(Api.url("/privacy.html")) } catch (_: Exception) {} },
        )
        Text(
            L("Effacer ma progression", "Erase my progress").uppercase(), style = textFont(14, W.HEAVY), color = Coral,
            modifier = Modifier.tappable { confirmErase = true },
        )
        if (AppStore.signedIn) {
            Text(
                L("Supprimer mon compte", "Delete my account").uppercase(), style = textFont(14, W.HEAVY), color = Coral,
                modifier = Modifier.tappable { password = ""; confirmDelete = true },
            )
        }
    }

    if (editing) Sheet(onDismiss = { editing = false }, expanded = false) { EditProfile(scope, onClose = { editing = false }) }
    auth?.let { m -> Sheet(onDismiss = { auth = null }) { AuthView(initial = m, onClose = { auth = null }) } }
    if (confirmErase) {
        Confirm(
            L("Effacer ta progression ?", "Erase your progress?"), L("XP, série, mots appris : tout repart de zéro.", "XP, streak, words learned: everything starts over."),
            L("Tout effacer", "Erase everything"), onDismiss = { confirmErase = false },
        ) { AppStore.eraseProgress() }
    }
    if (confirmSignOut) {
        Confirm(
            L("Se déconnecter ?", "Sign out?"), L("Ta progression reste sur ton compte.", "Your progress stays on your account."),
            L("Déconnexion", "Sign out"), onDismiss = { confirmSignOut = false },
        ) { scope.launch { AppStore.signOut() } }
    }
    if (confirmDelete) {
        val failed = L("La suppression a échoué.", "Deletion failed.")
        Confirm(
            L("Supprimer ton compte ?", "Delete your account?"),
            L("Ton compte et toute ta progression seront supprimés. C’est irréversible.", "Your account and all your progress will be deleted. This cannot be undone."),
            L("Supprimer définitivement", "Delete permanently"), onDismiss = { confirmDelete = false },
            field = if (AppStore.learner?.passwordLogin != false) {
                { LobaTextField(password, { password = it }, placeholder = L("Mot de passe", "Password"), password = true) }
            } else null,
        ) {
            val typed = password
            scope.launch {
                try {
                    AppStore.deleteAccount(typed)
                    accountError = ""
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    accountError = (e as? ApiError)?.message ?: failed
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatCell(art: String, value: String, label: String) {
    Row(Modifier.weight(1f).card().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(art, 34.dp)
        Column(Modifier.weight(1f)) {
            Text(value, style = displayFont(22), color = Ink)
            Text(label, style = textFont(13), color = Muted)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = displayFont(22), color = Ink, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun SettingRow(label: String, sub: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .drawBehind { drawRect(Line, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = textFont(17, W.BOLD), color = Ink)
            if (sub != null) Text(sub, style = textFont(14), color = Muted)
        }
        trailing()
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, flip: () -> Unit) {
    SettingRow(label) {
        Switch(
            checked = on, onCheckedChange = { Sfx.play(Sfx.Cue.TAP); flip() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Sky, checkedBorderColor = Sky,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = LineShade, uncheckedBorderColor = LineShade,
            ),
        )
    }
}

/** The iOS menu picker: the current choice in blue, opening a list of the others. */
@Composable
private fun <T> PickerMenu(selected: T, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.tappable { open = true }.defaultMinSize(minHeight = 44.dp).padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "", style = textFont(17), color = Sky)
            Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp), tint = Sky)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color.White) {
            for ((value, label) in options) {
                DropdownMenuItem(
                    text = { Text(label, style = textFont(17, if (value == selected) W.HEAVY else W.SEMIBOLD), color = if (value == selected) Sky else Ink) },
                    onClick = { open = false; onPick(value) },
                )
            }
        }
    }
}

/** Swift's `.capitalized`: "lingala" → "Lingala". */
private fun capitalized(text: String): String = text.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

/** A question with a destructive answer and "Cancel": the iOS alerts of this page. */
@Composable
private fun Confirm(title: String, message: String, action: String, onDismiss: () -> Unit, field: (@Composable () -> Unit)? = null, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, style = displayFont(22), color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(message, style = textFont(16), color = Ink2)
                field?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(action, style = textFont(16, W.HEAVY), color = Coral) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Annuler", "Cancel"), style = textFont(16, W.BOLD), color = Sky) } },
    )
}

/** `scope` outlives the sheet, so the new name still reaches the server after it closes. */
@Composable
private fun EditProfile(scope: CoroutineScope, onClose: () -> Unit) {
    var name by remember { mutableStateOf(AppStore.state.name.ifEmpty { AppStore.learner?.name ?: "" }) }
    var avatar by remember { mutableStateOf(AppStore.state.avatar) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(L("Ton profil", "Your profile"), style = displayFont(26), color = Ink)
        LobaTextField(name, { name = it }, placeholder = L("Ton prénom", "Your first name"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in Game.avatars.chunked(6)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (a in row) {
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            Modifier.weight(1f).heightIn(min = 50.dp).clip(shape).background(if (avatar == a) SkyTint else Surface)
                                .border(2.dp, if (avatar == a) Sky else Line, shape)
                                .tappable { Sfx.play(Sfx.Cue.TILE, Game.avatars.indexOf(a)); avatar = a },
                            contentAlignment = Alignment.Center,
                        ) { Text(a, fontSize = 28.sp, color = Ink) }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        ChunkyButton(L("Enregistrer", "Save"), kind = Chunky.GREEN) {
            val trimmed = name.trim()
            AppStore.setProfile(trimmed, avatar)
            if (AppStore.signedIn && trimmed.isNotEmpty()) {
                scope.launch {
                    try { AppStore.rename(trimmed) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                }
            }
            onClose()
        }
    }
}
