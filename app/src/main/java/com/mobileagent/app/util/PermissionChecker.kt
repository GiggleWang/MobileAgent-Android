package com.mobileagent.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object PermissionChecker {

    enum class Permission { ACCESSIBILITY, OVERLAY }

    fun getMissingCriticalPermissions(context: Context): List<Permission> {
        val missing = mutableListOf<Permission>()
        if (!isAccessibilityServiceEnabled(context)) missing.add(Permission.ACCESSIBILITY)
        if (!Settings.canDrawOverlays(context)) missing.add(Permission.OVERLAY)
        return missing
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
