package com.example.data.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.QuranDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = QuranDatabase.getDatabase(context)
                val settings = db.quranDao().getUserSettings().firstOrNull()
                
                // Only send if notifications are enabled
                if (settings == null || settings.dailyNotificationEnabled) {
                    showReminderNotification(context)
                }
                
                // Reschedule for tomorrow
                DailyReminderScheduler.scheduleNextDailyReminder(
                    context, 
                    settings?.notificationHour ?: 9, 
                    settings?.notificationMinute ?: 0
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showReminderNotification(context: Context) {
        val channelId = "quran_daily_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "দৈনিক কুরআন রিমাইন্ডার"
            val desc = "প্রতিদিন পবিত্র কুরআন চর্চার চমৎকার রিমাইন্ডার"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = desc
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            202,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messages = listOf(
            "আস-সালামু আলাইকুম। আজ কি আল-কুরআন পাঠ করেছেন? চলুন আজকের সুন্দর দিনটি পবিত্র কুরআনের আলোয় শুরু করি।",
            "কুরআন পড়ুন, তা হৃদয়কে প্রশান্ত করে এবং দ্বীনের আলো দিয়ে জীবনকে সুন্দর করে সাজিয়ে তোলে। ✨",
            "আজকের হাজারো ব্যস্ততার মাঝেও কি কিছু মূহুর্ত আল্লাহর বাণী পড়ার জন্য বরাদ্দ করা যায় না? চলুন তিলওয়াত করি!",
            "পবিত্র কুরআন তিলওয়াত ও শ্রবণ করা আমাদের ঈমানকে বৃদ্ধি করে। চলুন আজ অন্তত ৫ মিনিট হলেও কুরআন পড়ি বা শুনি।"
        )
        val selectedMessage = messages.random()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.drawable.ic_quran_notification)
            .setContentTitle("কুরআন তিলওয়াতের স্মরণিকা ✨")
            .setContentText(selectedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(selectedMessage))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0xFF0F9F59.toInt()) // Standard matching emerald primary color
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(7722, notification)
    }
}
