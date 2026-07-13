package com.middepremkumar.autosilent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class QuickSilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        prefs.setQuickSilenceEnd(0L)
        
        // Log Analytics
        val bundle = Bundle().apply {
            putString("rule_type", "quick_silence")
            putString("action", "RESTORE")
        }
        FirebaseAnalytics.getInstance(context).logEvent("silence_rule_triggered", bundle)

        // Restore to RING mode
        AlarmReceiver.applyRingerMode(context, RingerChoice.RING, -1)
        
        // Update UI if app is open (we'll use a local broadcast or just let the app refresh onResume)
        val refreshIntent = Intent("com.middepremkumar.autosilent.REFRESH_UI")
        context.sendBroadcast(refreshIntent)
    }
}
