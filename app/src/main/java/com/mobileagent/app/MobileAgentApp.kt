package com.mobileagent.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class MobileAgentApp : Application() {

    companion object {
        const val CHANNEL_ID = "mobile_agent_service"
        const val ALERT_CHANNEL_ID = "mobile_agent_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "MobileAgent Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps MobileAgent running in the background"
        }

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "MobileAgent Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important alerts about service status"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(serviceChannel, alertChannel))
    }
}
