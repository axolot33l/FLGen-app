package fi.junixald.NutellaService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesManager(context)
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                if (prefs.startOnBoot.first()) {
                    CodeNotificationService.start(context)
                }
            }
        }
    }
}
