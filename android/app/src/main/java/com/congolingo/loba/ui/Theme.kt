package com.congolingo.loba.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.congolingo.loba.R

// Palette and type, matching the website

/** "#1c9bef" or "1c9bef". */
fun hexColor(hex: String): Color = Color(0xFF000000 or (hex.trim().removePrefix("#").toLongOrNull(16) ?: 0L))

val Sky = hexColor("1c9bef"); val SkyShade = hexColor("1479bf"); val SkyTint = hexColor("e1f1fd")
val Leaf = hexColor("2bb673"); val LeafShade = hexColor("1f9259"); val LeafTint = hexColor("dcf5e8")
val Sun = hexColor("ffc22e"); val SunShade = hexColor("e09f00"); val SunTint = hexColor("fff4d6")
val Coral = hexColor("f0524f"); val CoralShade = hexColor("c93a3c"); val CoralTint = hexColor("ffe3e1")
val Plum = hexColor("8b5cf6"); val PlumShade = hexColor("6d3fe0")
val Ink = hexColor("2a2540"); val Ink2 = hexColor("5b5670"); val Muted = hexColor("8f8aa3")
val Line = hexColor("ece5db"); val LineShade = hexColor("ddd3c4"); val Surface = hexColor("f9f5ef")
val Cocoa = hexColor("5a3a22"); val Paper = hexColor("fff6e6"); val Sand = hexColor("f3e3c3")

/** Nunito's weights, as the iOS app names them (`.text(17, .heavy)`). */
enum class W(val value: Int) { REGULAR(550), SEMIBOLD(650), BOLD(750), HEAVY(850), BLACK(900) }

// The website's typefaces, shipped with the app as variable fonts: Fredoka for headings, Nunito for everything else.
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    FontFamily(Font(res, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))))

val Fredoka: FontFamily = variable(R.font.fredoka, 650)
private val nunito = W.entries.associateWith { variable(R.font.nunito, it.value) }
fun nunito(weight: W = W.SEMIBOLD): FontFamily = nunito.getValue(weight)

private val tight = PlatformTextStyle(includeFontPadding = false)

/** Headings: `.font(.display(30))` on iOS. */
fun displayFont(size: Int): TextStyle = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight(650), fontSize = size.sp, platformStyle = tight)

/** Everything else: `.font(.text(17, .heavy))` on iOS. */
fun textFont(size: Int, weight: W = W.SEMIBOLD): TextStyle =
    TextStyle(fontFamily = nunito(weight), fontWeight = FontWeight(weight.value), fontSize = size.sp, platformStyle = tight)
