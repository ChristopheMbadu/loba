package com.congolingo.loba.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.L
import com.congolingo.loba.core.Prefs
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** The learner's own words and sentences, kept on this phone: one notebook per account and language. */
object Notebook {
    data class Entry(val id: String = UUID.randomUUID().toString(), val term: String, val translation: String, val sentence: Boolean)

    fun key(): String = "phrasebook.${AppStore.learner?.email?.lowercase() ?: "guest"}.${AppStore.state.lang}"

    fun read(key: String): List<Entry> {
        val saved = Prefs.store.getString(key, null) ?: return emptyList()
        return try {
            val list = JSONArray(saved)
            (0 until list.length()).mapNotNull { i ->
                val o = list.optJSONObject(i) ?: return@mapNotNull null
                Entry(o.optString("id").ifEmpty { UUID.randomUUID().toString() }, o.optString("term"), o.optString("translation"), o.optBoolean("sentence"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun write(key: String, entries: List<Entry>) {
        val list = JSONArray()
        for (e in entries) list.put(JSONObject().put("id", e.id).put("term", e.term).put("translation", e.translation).put("sentence", e.sentence))
        Prefs.store.edit().putString(key, list.toString()).apply()
    }
}

/** The notebook's editor. Shown in a `Sheet`. */
@Composable
fun PhrasebookView(key: String, onClose: () -> Unit) {
    var entries by remember(key) { mutableStateOf(Notebook.read(key)) }
    var term by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var sentence by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(setOf<String>()) }

    fun save(next: List<Notebook.Entry>) {
        entries = next
        Notebook.write(key, next)
    }

    Column(Modifier.fillMaxWidth().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(L("Mon carnet", "My phrasebook"), Modifier.weight(1f), style = displayFont(26), color = Ink)
            PhrasebookLink(L("Terminé", "Done"), Modifier.padding(horizontal = 12.dp), W.BOLD, onClose)
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "form") {
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhrasebookKind(listOf(L("Mot ou expression", "Word or phrase"), L("Phrase", "Sentence")), if (sentence) 1 else 0) { sentence = it == 1 }
                    LobaTextField(term, { term = it }, placeholder = L("Dans la langue apprise", "In the language you’re learning"), singleLine = false)
                    LobaTextField(translation, { translation = it }, placeholder = L("Traduction", "Translation"), singleLine = false)
                    ChunkyButton(L("Enregistrer", "Save"), enabled = term.trim().isNotEmpty() && translation.trim().isNotEmpty()) {
                        save(listOf(Notebook.Entry(term = term.trim(), translation = translation.trim(), sentence = sentence)) + entries)
                        term = ""
                        translation = ""
                    }
                }
            }
            item(key = "footer") {
                Text(
                    L("Carnet privé sur cet appareil. Tes mots et expressions sont disponibles dans Devine le mot.", "Private to this device. Your words and phrases are available in Guess the Word."),
                    Modifier.padding(horizontal = 4.dp), style = textFont(13), color = Muted,
                )
            }
            items(entries, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth().card().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (entry.sentence) entry.translation else entry.term, style = textFont(17, W.BOLD), color = Ink)
                        if (!entry.sentence) Text(entry.translation, style = textFont(17), color = Ink)
                        else if (entry.id in revealed) Text(entry.term, style = textFont(17), color = Ink)
                        else PhrasebookLink(L("Révéler la phrase", "Reveal sentence"), Modifier, W.SEMIBOLD) { revealed = revealed + entry.id }
                    }
                    // Swipe to delete on iOS; a button here.
                    Box(Modifier.size(44.dp).tappable { save(entries.filter { it.id != entry.id }) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Delete, L("Supprimer", "Delete"), Modifier.size(22.dp), tint = Coral)
                    }
                }
            }
        }
    }
}

/** A plain text button in the accent colour. */
@Composable
private fun PhrasebookLink(text: String, modifier: Modifier, weight: W, onClick: () -> Unit) {
    Box(modifier.defaultMinSize(minHeight = 44.dp).tappable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(text, style = textFont(17, weight), color = Sky)
    }
}

/** The segmented control between "word" and "sentence". */
@Composable
private fun PhrasebookKind(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Line).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(9.dp)).background(if (i == selected) Color.White else Color.Transparent)
                    .tappable { onSelect(i) }.padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = textFont(14, if (i == selected) W.HEAVY else W.SEMIBOLD), color = if (i == selected) Ink else Ink2, textAlign = TextAlign.Center) }
        }
    }
}
