package com.congolingo.loba.ui

import com.congolingo.loba.core.Music

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.congolingo.loba.core.L
import com.congolingo.loba.core.LibrarySong
import com.congolingo.loba.core.SongLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private val songColors = listOf(Sky, Plum, Coral, Leaf, Color(0xffe2a528), Color(0xffeb73a6))

@Composable
fun SongGarden(onClose: () -> Unit) {
    val library = remember { SongLibrary.shared }
    val scope = rememberCoroutineScope()
    var favoritesOnly by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(library) { library.open() }
    DisposableEffect(library) { onDispose { library.leave() } }
    val visible = library.songs.filter { !favoritesOnly || it.id in library.favorites }
    val accent = songColors[library.selected?.artwork ?: 0]
    // A tablet on its side: the stage stays on the left and the songs scroll beside it.
    val wide = LocalConfiguration.current.let { it.screenWidthDp >= 840 && it.screenWidthDp > it.screenHeightDp }
    val header: @Composable () -> Unit = {
        Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(L("LE COIN MUSIQUE","LOBA’S MUSIC CORNER"),style=textFont(11,W.HEAVY),letterSpacing=2.sp,color=Plum)
                Text(L("Le jardin des chansons","The Song Garden"),style=displayFont(30),color=Ink)
                Text(L("Écoute. Danse. Chante avec nous !","Listen. Dance. Sing along!"),style=textFont(16),color=Ink2)
            }
            SongControl(Icons.Rounded.Close,L("Fermer","Close"),onClick=onClose)
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xfffcf9f2)).systemBarsPadding()) {
        val grid: @Composable (Modifier) -> Unit = { place ->
        LazyVerticalGrid(columns=GridCells.Adaptive(148.dp),modifier=place,contentPadding=if(wide)PaddingValues(vertical=20.dp) else PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=if(library.selected!=null)120.dp else 20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            if(!wide) {
                item(span={GridItemSpan(maxLineSpan)}) { header() }
                item(span={GridItemSpan(maxLineSpan)}) { SongStage(library,accent) }
            }
            if (Music.mutedAll || !Music.enabled) item(span={GridItemSpan(maxLineSpan)}) {
                Text(L("La musique est coupée dans Profil → Réglages.", "Music is muted in Profile → Settings."), style=textFont(14,W.BOLD), color=Ink2, modifier=Modifier.padding(12.dp))
            }
            if(library.loading) item(span={GridItemSpan(maxLineSpan)}) {
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp),color=Sky);Spacer(Modifier.width(12.dp));Text(L("On prépare la musique…","Warming up the music…"),color=Ink2)
                }
            }
            if(library.error) item(span={GridItemSpan(maxLineSpan)}) {
                Column(Modifier.background(Color.White,RoundedCornerShape(22.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(L("La chanson n’arrive pas. Vérifie ta connexion et réessaie.","The song couldn’t load. Check your connection and try again."),style=textFont(15),color=Ink)
                    ChunkyButton(L("Réessayer","Try again"),kind=Chunky.SKY) { scope.launch { val song=library.selected;if(song!=null)library.play(song) else library.load() } }
                }
            }
            if(library.songs.isNotEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    FilterChip(selected=!favoritesOnly,onClick={favoritesOnly=false},label={Text(L("Toutes","All songs"),style=textFont(14,W.HEAVY))},modifier=Modifier.heightIn(min=44.dp))
                    FilterChip(selected=favoritesOnly,onClick={favoritesOnly=true},label={Text(L("Mes favoris","My favorites"),style=textFont(14,W.HEAVY))},modifier=Modifier.heightIn(min=44.dp))
                    Spacer(Modifier.weight(1f));Text("${visible.size}",style=textFont(16,W.HEAVY),color=Ink2)
                }
            }
            items(visible,key={it.id}) { song -> SongCard(song,library) }
            if(!library.loading && visible.isEmpty() && !library.error) item(span={GridItemSpan(maxLineSpan)}) {
                Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Art("heart",42.dp)
                    Text(if(favoritesOnly)L("Tes chansons préférées t’attendent ici.","Your favorite songs will live here.") else L("Les chansons arrivent bientôt !","Songs are on their way!"),style=textFont(17,W.HEAVY),color=Ink)
                    if(favoritesOnly)Text(L("Touche le cœur d’une chanson pour la garder.","Tap a song’s heart to save it here."),style=textFont(14),color=Ink2)
                }
            }
            item(span={GridItemSpan(maxLineSpan)}) {
                Text("🇨🇩  "+L("Les chansons de chez nous","The songs we grew up with"),style=textFont(13),color=Ink2,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        }
        if(wide) Row(Modifier.fillMaxSize().readable(1240.dp).padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(0.85f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                header()
                SongStage(library,accent)
            }
            grid(Modifier.weight(1.15f).fillMaxHeight())
        } else grid(Modifier.fillMaxSize().readable(760.dp))
        if(!wide) library.selected?.let { song ->
            Row(Modifier.align(Alignment.BottomCenter).readable(760.dp).padding(horizontal=16.dp,vertical=8.dp).fillMaxWidth().background(Ink,RoundedCornerShape(24.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.MusicNote,null,Modifier.size(30.dp),tint=Sun)
                Column(Modifier.weight(1f)) {
                    Text(L("À L’ÉCOUTE","NOW PLAYING"),style=textFont(10,W.HEAVY),letterSpacing=1.sp,color=Color.White.copy(alpha=0.65f))
                    Text(song.title,style=textFont(16,W.HEAVY),color=Color.White,maxLines=2)
                }
                FilledIconButton(onClick=library::toggle,modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Sun,contentColor=Ink)) {
                    Icon(if(library.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(library.playing)L("Pause","Pause") else L("Écouter","Play"))
                }
                IconButton(onClick={library.next()},modifier=Modifier.size(44.dp)) { Icon(Icons.Rounded.SkipNext,L("Chanson suivante","Next song"),tint=Color.White) }
            }
        }
    }
}

@Composable
private fun SongStage(library:SongLibrary,accent:Color) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(library.playing) {
        phase=0f
        if(library.playing && ValueAnimator.areAnimatorsEnabled())while(isActive){delay(50);phase+=0.16f}
    }
    val beat=if(library.playing)sin(phase) else 0f
    Column(Modifier.fillMaxWidth().padding(bottom=5.dp).background(Line,RoundedCornerShape(28.dp)).padding(bottom=5.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).border(2.dp,Line,RoundedCornerShape(28.dp))) {
        Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(28.dp)).background(accent.copy(alpha=0.12f)),contentAlignment=Alignment.Center) {
            Box(Modifier.offset(x=100.dp,y=(-42).dp).size(94.dp).background(Sun.copy(alpha=0.65f),CircleShape))
            Box(Modifier.offset(y=72.dp).size(260.dp,70.dp).background(accent.copy(alpha=0.2f),CircleShape))
            repeat(5) { n ->
                Text(if(n%2==0)"♪" else "♫",fontSize=(22+n*3).sp,color=songColors[n].copy(alpha=0.8f),modifier=Modifier.offset(x=((if(n%2==0)-1 else 1)*(85+n*9)).dp,y=(-65+n*26+beat*6).dp).rotate(n*12-20+beat*5))
            }
            if(ValueAnimator.areAnimatorsEnabled())LobaView(if(library.playing)Mood.CHEER else Mood.HAPPY,170.dp,tappable=false,modifier=Modifier.rotate(beat*5).offset(y=(-beat*5).dp))
            else Art("navProfile",155.dp)
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp).height(26.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
                repeat(11) { n -> Box(Modifier.width(5.dp).height((8+if(library.playing)abs(sin(n.toFloat()+beat))*14 else 2f).dp).background(accent.copy(alpha=0.7f),RoundedCornerShape(3.dp))) }
            }
        }
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(library.selected?.title ?: L("Un peu de musique, beaucoup de joie.","Little songs. Big smiles."),style=displayFont(25),color=Ink,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            if(library.selected!=null) {
                Column {
                    Slider(value=library.elapsed.coerceIn(0f,maxOf(1f,library.duration)),onValueChange=library::seek,valueRange=0f..maxOf(1f,library.duration),enabled=library.duration>0,modifier=Modifier.semantics { contentDescription=L("Position dans la chanson","Song position") },colors=SliderDefaults.colors(thumbColor=accent,activeTrackColor=accent))
                    Row(Modifier.fillMaxWidth()) { Text(SongLibrary.clock(library.elapsed),style=textFont(12),color=Ink2);Spacer(Modifier.weight(1f));Text(SongLibrary.clock(library.duration),style=textFont(12),color=Ink2) }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    SongControl(if(library.repeatSong)Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,L("Répéter la chanson","Repeat song"),selected=library.repeatSong) {library.repeatSong=!library.repeatSong}
                    SongControl(Icons.Rounded.SkipPrevious,L("Chanson précédente","Previous song")) {library.next(-1)}
                    FilledIconButton(onClick=library::toggle,modifier=Modifier.size(62.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=accent,contentColor=Color.White)) {
                        if(library.buffering)CircularProgressIndicator(Modifier.size(25.dp),color=Color.White)
                        else Icon(if(library.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(library.playing)L("Pause","Pause") else L("Écouter","Play"),Modifier.size(32.dp))
                    }
                    SongControl(Icons.Rounded.SkipNext,L("Chanson suivante","Next song")) {library.next()}
                    SongControl(Icons.Rounded.Shuffle,L("Surprends-moi","Surprise me")) {library.surprise()}
                }
            } else {
                Text(L("Des chansons d’enfance à partager, encore et encore.","Childhood favorites to share, again and again."),style=textFont(15),color=Ink2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                ChunkyButton(L("Surprends-moi !","Surprise me!"),kind=Chunky.SUN,enabled=library.songs.isNotEmpty(),leading={Icon(Icons.Rounded.Shuffle,null)}) {library.surprise()}
            }
        }
    }
}

@Composable
private fun SongCard(song:LibrarySong,library:SongLibrary) {
    val color=songColors[song.artwork];val active=library.selected?.id==song.id
    Column(Modifier.padding(bottom=4.dp).background(Line,RoundedCornerShape(24.dp)).padding(bottom=4.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).border(2.dp,if(active)color else Line,RoundedCornerShape(24.dp)).padding(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(135.dp).clip(RoundedCornerShape(20.dp)).background(color.copy(alpha=0.18f)).clickable {library.play(song)}.semantics { contentDescription=L("Écouter","Play")+" "+song.title },contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color.copy(alpha=0.14f),radius=size.width*0.4f,center=Offset(size.width,size.height*0.1f))
                drawCircle(Color.White.copy(alpha=0.5f),radius=size.width*0.4f,center=Offset(0f,size.height))
                val radius=size.minDimension*0.36f
                drawCircle(Ink,radius)
                repeat(3){drawCircle(Color.White.copy(alpha=0.14f),radius-(10+it*8).dp.toPx(),style=Stroke(1.dp.toPx()))}
                drawCircle(color,radius*0.4f)
            }
            KubaBand(Modifier.align(Alignment.BottomCenter),color=color,background=color.copy(alpha=0.08f),height=12.dp)
            Icon(Icons.Rounded.MusicNote,null,Modifier.size(24.dp),tint=Color.White)
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(38.dp).background(Color.White,CircleShape),contentAlignment=Alignment.Center) {
                Icon(if(active&&library.playing)Icons.Rounded.VolumeUp else Icons.Rounded.PlayArrow,null,Modifier.size(22.dp),tint=Ink)
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
            Text(song.title,style=textFont(17,W.HEAVY),color=Ink,modifier=Modifier.weight(1f).padding(start=4.dp,top=4.dp))
            IconButton(onClick={library.favorite(song)},modifier=Modifier.size(44.dp)) {
                Icon(if(song.id in library.favorites)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,(if(song.id in library.favorites)L("Retirer des favoris","Remove favorite") else L("Ajouter aux favoris","Add favorite"))+" "+song.title,tint=Coral)
            }
        }
    }
}

@Composable
private fun SongControl(icon:ImageVector,label:String,selected:Boolean=false,onClick:()->Unit) {
    IconButton(onClick=onClick,modifier=Modifier.size(44.dp).background(if(selected)Plum.copy(alpha=0.14f) else Surface,CircleShape)) {
        Icon(icon,label,Modifier.size(22.dp),tint=if(selected)Plum else Ink)
    }
}
