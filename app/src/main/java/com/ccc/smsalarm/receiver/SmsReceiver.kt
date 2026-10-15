package com.ccc.smsalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ccc.smsalarm.di.AppEntryPoint
import com.ccc.smsalarm.service.AlarmService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Manual Hilt injection — more reliable than @AndroidEntryPoint when process is recreated
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppEntryPoint::class.java
        )

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        // Long SMS arrives as multiple PDUs — concatenate before matching,
        // otherwise a keyword spanning two fragments never matches and each
        // fragment would trigger a separate alarm.
        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val matchedRule = entryPoint.alarmRuleRepository().matchSms(body) ?: return@launch

                val matchedKeywords = matchedRule.keywords.filter {
                    body.contains(it, ignoreCase = true)
                }

                entryPoint.matchedSmsRepository().insertSms(
                    sender = sender,
                    body = body,
                    matchedKeywords = matchedKeywords,
                    matchedRuleId = matchedRule.id
                )

                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmService.EXTRA_SENDER, sender)
                    putExtra(AlarmService.EXTRA_BODY, body)
                }
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                // A DB failure or a background FGS start restriction (Android 12+,
                // e.g. when MonitorService was killed) must never crash the process —
                // surface the alarm as a full-screen notification instead.
                AlarmService.postFallbackNotification(context, sender, body)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
