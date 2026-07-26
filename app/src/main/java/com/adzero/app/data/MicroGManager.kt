package com.adzero.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MicroGManager {
    var isLoggedIn by mutableStateOf(false)
        private set

    var accountName by mutableStateOf("AdZero User")
        private set

    var accountEmail by mutableStateOf("Not connected")
        private set

    var avatarUrl by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {}

    fun saveAccount(context: Context, name: String, email: String, avatar: String? = null) {
        accountName = name
        accountEmail = email
        avatarUrl = avatar
        isLoggedIn = true
    }
}
