package com.ninmods.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ninmods.menu.ui.theme.*

@Composable
fun MainScreen(
    onStartMenu: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit
) {
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
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.account_active),
            color = NinTextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        Button(
            onClick = onStartMenu,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NinPrimary),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.btn_start_menu), color = NinBackground, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp)
        ) {
            Text(stringResource(R.string.btn_logout), color = NinPrimary)
        }

        TextButton(
            onClick = onExit,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(stringResource(R.string.btn_exit), color = NinTextSecondary)
        }
    }
}
