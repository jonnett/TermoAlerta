package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

/** Notificaciones de alerta cuando la temperatura supera el límite configurado. */
object AlertNotifier {
    private const val CHANNEL_LOUD = "alerta_temperatura_sonido"
    private const val CHANNEL_QUIET = "alerta_temperatura_silencio"
    private const val CHANNEL_STATUS = "estado_temperatura"
    private const val ID_ALERT = 1001

    private val VIBRATION = longArrayOf(0, 600, 250, 600, 250, 900)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val loud = NotificationChannel(CHANNEL_LOUD, "Alertas de temperatura", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa con sonido y vibración cuando se supera el límite"
            enableVibration(true)
            vibrationPattern = VIBRATION
            enableLights(true)
            lightColor = Color.RED
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        val quiet = NotificationChannel(CHANNEL_QUIET, "Alertas silenciosas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alertas de temperatura sin sonido ni vibración"
            enableVibration(false)
            setSound(null, null)
        }
        val status = NotificationChannel(CHANNEL_STATUS, "Temperatura normalizada", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisa cuando la temperatura vuelve a estar bajo el límite"
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(loud, quiet, status))
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showAlert(context: Context, celsius: Float, limit: Float, sensorName: String) {
        val channel = if (AppSettings.soundEnabled.value) CHANNEL_LOUD else CHANNEL_QUIET
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_thermo)
            .setContentTitle("¡Temperatura sobre el límite!")
            .setContentText("${formatC(celsius)} medidos en $sensorName (límite ${formatC(limit)})")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(Color.rgb(198, 40, 40))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        notify(context, notification)
    }

    fun showBackToNormal(context: Context, celsius: Float, limit: Float) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_thermo)
            .setContentTitle("Temperatura normalizada")
            .setContentText("Ahora ${formatC(celsius)}, bajo el límite de ${formatC(limit)}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        notify(context, notification)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, notification: android.app.Notification) {
        if (!canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(ID_ALERT, notification)
        } catch (e: SecurityException) {
            // el usuario quitó el permiso mientras la app estaba abierta
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

fun formatC(celsius: Float): String = String.format(Locale.getDefault(), "%.1f °C", celsius)
