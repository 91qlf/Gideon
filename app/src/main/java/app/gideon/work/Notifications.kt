package app.gideon.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.gideon.MainActivity
import app.gideon.R
import app.gideon.data.NewsItem

object Notifications {
    private const val CHANNEL = "brief"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Brief du jour", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Le résumé de l'actualité chaque matin"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showBrief(context: Context, headlines: List<NewsItem>, total: Int) {
        if (headlines.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = NotificationCompat.InboxStyle().setBigContentTitle("Ton brief du jour")
        headlines.forEach { style.addLine("• ${it.title}") }
        style.setSummaryText("$total infos à scroller")

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_gideon)
            .setColor(0xFF2FE6FF.toInt())
            .setContentTitle("Ton brief du jour")
            .setContentText(headlines.first().title)
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, notification)
        } catch (_: SecurityException) {
        }
    }
}
