package com.campusflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.content.Intent
import com.campusflow.app.ui.AppViewModel
import com.campusflow.app.ui.CampusRoot

class MainActivity : ComponentActivity() {
    private val vm by viewModels<AppViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampusRoot(vm)
        }
        openNotification(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openNotification(intent) }
    private fun openNotification(intent: Intent) {
        val id = intent.getLongExtra("occurrenceId", -1)
        if (id > 0) vm.notificationTarget.value = id
    }
}
