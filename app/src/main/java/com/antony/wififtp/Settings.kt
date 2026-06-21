package com.antony.wififtp

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persisted settings avoids any external DB dependency to
 * keep the app footprint small. Backed by a single SharedPreferences file.
 */
object Settings {
    private const val PREFS_NAME = "helga_prefs"
    private const val KEY_PORT = "port"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_AUTO_START = "auto_start_on_boot"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    var port: Int
        get() = prefs.getInt(KEY_PORT, 2121)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "android") ?: "android"
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "1234") ?: "1234"
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
}
