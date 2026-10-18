package com.ninmods.menu.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.ninmods.menu.data.PrefsHelper

object AuthManager {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val currentUser: FirebaseUser?
        get() = try { auth.currentUser } catch (_: Exception) { null }

    fun currentUser(): String? = currentUser?.email ?: if (PrefsHelper.isLoggedIn()) "free" else null

    val isLoggedIn: Boolean
        get() = PrefsHelper.isLoggedIn() || (currentUser != null)

    fun signIn(
        email: String,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val u = email.trim()
        val p = pass.trim()
        if (u.isBlank() || p.isBlank()) {
            onResult(false, "Username and password cannot be empty")
            return
        }

        // Free mode credentials
        if (u.equals("free", ignoreCase = true) && p.equals("free", ignoreCase = true)) {
            PrefsHelper.setLoggedIn(true)
            onResult(true, null)
            return
        }

        try {
            auth.signInWithEmailAndPassword(u, p)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        PrefsHelper.setLoggedIn(true)
                        onResult(true, null)
                    } else {
                        onResult(false, task.exception?.localizedMessage ?: "Invalid login (Use: free / free)")
                    }
                }
        } catch (_: Exception) {
            onResult(false, "Firebase unavailable. Use user: free / pass: free")
        }
    }

    fun register(
        email: String,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val u = email.trim()
        val p = pass.trim()
        if (u.isBlank() || p.isBlank()) {
            onResult(false, "Username and password cannot be empty")
            return
        }

        if (u.equals("free", ignoreCase = true) && p.equals("free", ignoreCase = true)) {
            PrefsHelper.setLoggedIn(true)
            onResult(true, null)
            return
        }

        try {
            auth.createUserWithEmailAndPassword(u, p)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        PrefsHelper.setLoggedIn(true)
                        onResult(true, null)
                    } else {
                        onResult(false, task.exception?.localizedMessage ?: "Registration failed")
                    }
                }
        } catch (_: Exception) {
            onResult(false, "Firebase unavailable. Use user: free / pass: free")
        }
    }

    fun signOut() {
        PrefsHelper.setLoggedIn(false)
        try {
            auth.signOut()
        } catch (_: Exception) {}
    }
}
