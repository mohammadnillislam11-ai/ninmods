package com.ninmods.menu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ninmods.menu.auth.AuthManager
import com.ninmods.menu.ui.theme.*

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AuthManager.isLoggedIn) {
            navigateToMain()
            return
        }

        setContent {
            NinmodsMenuTheme {
                LoginScreen(
                    onLoginSuccess = { navigateToMain() }
                )
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("free") }
    var password by remember { mutableStateOf("free") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NinBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.login_title),
            color = NinPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 36.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Username (free)", color = NinTextSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NinPrimary,
                unfocusedBorderColor = NinTextSecondary,
                focusedTextColor = NinTextPrimary,
                unfocusedTextColor = NinTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (free)", color = NinTextSecondary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NinPrimary,
                unfocusedBorderColor = NinTextSecondary,
                focusedTextColor = NinTextPrimary,
                unfocusedTextColor = NinTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                AuthManager.signIn(email, password) { success, err ->
                    isLoading = false
                    if (success) onLoginSuccess() else errorMessage = err
                }
            },
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NinPrimary),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = NinBackground, modifier = Modifier.size(24.dp))
            } else {
                Text(stringResource(R.string.btn_login), color = NinBackground, fontWeight = FontWeight.Bold)
            }
        }

        errorMessage?.let { msg ->
            Text(text = msg, color = NinError, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }

        TextButton(
            onClick = {
                isLoading = true
                errorMessage = null
                AuthManager.register(email, password) { success, err ->
                    isLoading = false
                    if (success) onLoginSuccess() else errorMessage = err
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.btn_create_account), color = NinPrimary)
        }
    }
}
