package com.congolingo.loba.ui

import android.content.Intent
import com.congolingo.loba.BuildConfig

/**
 * For screenshots while developing (debug builds only), the iOS app's launch arguments as intent extras:
 * `adb shell am start -n com.congolingo.loba/.MainActivity --ez demo true --es screen story --ei page 2`
 *  demo: skip sign-in and onboarding · quick: skip the loading screen's minimum time · still: no decorative motion
 *  screen: story|lesson|build|review|puzzle|puzzles|map|practice|quests|shop|profile · page: story page
 *  autoplay: yes|wrong|hold (plays a lesson by itself)
 */
object DebugFlags {
    var quick = false; private set
    var demo = false; private set
    var still = false; private set
    var screen: String? = null; private set
    var page: Int? = null; private set
    var autoplay: String? = null; private set
    var maplevel: String? = null; private set

    fun read(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        quick = intent.getBooleanExtra("quick", false)
        demo = intent.getBooleanExtra("demo", false)
        still = intent.getBooleanExtra("still", false)
        screen = intent.getStringExtra("screen")
        page = if (intent.hasExtra("page")) intent.getIntExtra("page", 0) else null
        autoplay = intent.getStringExtra("autoplay")
        maplevel = intent.getStringExtra("maplevel")
    }
}
