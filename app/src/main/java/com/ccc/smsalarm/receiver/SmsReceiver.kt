package com.ccc.smsalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ccc.smsalarm.data.repository.AlarmRuleRepository
import com.ccc.smsalarm.data.repository.MatchedSmsRepository
import com.ccc.smsalarm.service.AlarmService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRuleRepository: AlarmRuleRepository
    @Inject lateinit var matchedSmsRepository: MatchedSmsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (smsMessage in messages) {
                    val sender = smsMessage.displayOriginatingAddress ?: continue
                    val body = smsMessage.messageBody ?: continue

                    val matchedRule = alarmRuleRepository.matchSms(body)
                    if (matchedRule != null) {
                        val matchedKeywords = matchedRule.keywords.filter {
                            body.contains(it, ignoreCase = true)
                        }

                        matchedSmsRepository.insertSms(
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
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
