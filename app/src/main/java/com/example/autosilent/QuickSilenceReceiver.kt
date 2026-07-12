package com.example.autosilent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class QuickSilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        prefs.setQuickSilenceEnd(0L)
        
        // Restore to RING mode
        AlarmReceiver.applyRingerMode(context, RingerChoice.RING, -1)
        
        // Update UI if app is open (we'll use a local broadcast or just let the app refresh onResume)
        val refreshIntent = Intent("com.example.autosilent.REFRESH_UI")
        context.sendBroadcast(refreshIntent)
    }
}
