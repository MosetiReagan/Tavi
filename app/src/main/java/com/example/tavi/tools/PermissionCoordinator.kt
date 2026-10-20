package com.example.tavi.tools

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.tavi.accessibility.TaviAccessibilityService
import com.example.tavi.model.AccessStatus
import com.example.tavi.model.SpecialAccessType
import com.example.tavi.model.ToolCategory
import com.example.tavi.roles.RoleManagerHelper
import com.example.tavi.tools.messages.TaviNotificationListenerService

data class CapabilityGroup(
    val category: ToolCategory,
    val title: String,
    val description: String,
    val requiredPermissions: List<String>,
    val specialAccessType: SpecialAccessType = SpecialAccessType.NONE,
    val isAvailableOnDevice: Boolean = true,
    val supportedOperations: List<String>,
    val limitations: String,
    val isGranted: Boolean
)

data class SpecialAccessItem(
    val type: SpecialAccessType,
    val title: String,
    val description: String,
    val status: AccessStatus,
    val isRole: Boolean = false,
    val securityNotice: String,
    val requiredApiLevel: Int = 24
)

object PermissionCoordinator {

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val myService = ComponentName(context, TaviAccessibilityService::class.java)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == myService.packageName &&
            it.resolveInfo.serviceInfo.name == myService.className
        } || TaviAccessibilityService.instance != null
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        val cn = ComponentName(context, TaviNotificationListenerService::class.java)
        return flat.contains(cn.flattenToString()) || flat.contains(context.packageName) || TaviNotificationListenerService.instance != null
    }

    fun hasSpecialAccess(context: Context, accessType: SpecialAccessType): Boolean {
        return when (accessType) {
            SpecialAccessType.NONE -> true
            SpecialAccessType.NOTIFICATION_LISTENER -> isNotificationListenerEnabled(context)
            SpecialAccessType.EXACT_ALARM -> canScheduleExactAlarms(context)
            SpecialAccessType.USAGE_STATS -> hasUsageStatsPermission(context)
            SpecialAccessType.ACCESSIBILITY_SERVICE -> isAccessibilityServiceEnabled(context)
            SpecialAccessType.DEFAULT_ASSISTANT -> RoleManagerHelper.getAssistantStatus(context) == AccessStatus.ROLE_HELD
            SpecialAccessType.DEFAULT_SMS_APP -> RoleManagerHelper.getSmsStatus(context) == AccessStatus.ROLE_HELD
            SpecialAccessType.DEFAULT_DIALER -> RoleManagerHelper.getDialerStatus(context) == AccessStatus.ROLE_HELD
            SpecialAccessType.OVERLAY_PERMISSION -> canDrawOverlays(context)
            SpecialAccessType.ALL_FILES_ACCESS -> hasAllFilesAccess(context)
            SpecialAccessType.BATTERY_OPTIMIZATION -> isBatteryOptimizationIgnored(context)
        }
    }

    fun getPermissionRationale(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_CALENDAR -> "Required to inspect your upcoming meetings and agenda."
            Manifest.permission.WRITE_CALENDAR -> "Required to schedule, edit, or delete events on your local calendar."
            Manifest.permission.READ_CONTACTS -> "Required to resolve contact names and phone numbers for messaging or calling."
            Manifest.permission.WRITE_CONTACTS -> "Required to create or update contacts upon your request."
            Manifest.permission.READ_SMS -> "Required to search your recent SMS inbox locally."
            Manifest.permission.SEND_SMS -> "Required to transmit confirmed text messages through your cellular carrier."
            Manifest.permission.CALL_PHONE -> "Required to initiate confirmed phone calls directly to your contacts."
            Manifest.permission.RECORD_AUDIO -> "Required for on-device speech-to-text recognition."
            Manifest.permission.POST_NOTIFICATIONS -> "Required to show reminder notifications on Android 13+."
            Manifest.permission.SCHEDULE_EXACT_ALARM, Manifest.permission.USE_EXACT_ALARM ->
                "Required to trigger exact reminders and alarms at your requested time."
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION ->
                "Required to query your current location when you ask for directions or location-based info."
            else -> "Required for local Android system integration."
        }
    }

    fun getSettingsIntent(accessType: SpecialAccessType, context: Context): Intent {
        return when (accessType) {
            SpecialAccessType.NOTIFICATION_LISTENER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                } else {
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                }
            }
            SpecialAccessType.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else {
                    getAppDetailsSettingsIntent(context)
                }
            }
            SpecialAccessType.USAGE_STATS -> {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            SpecialAccessType.ACCESSIBILITY_SERVICE -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            SpecialAccessType.DEFAULT_ASSISTANT -> {
                RoleManagerHelper.createRequestRoleIntent(context, SpecialAccessType.DEFAULT_ASSISTANT)
                    ?: Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            }
            SpecialAccessType.DEFAULT_SMS_APP -> {
                RoleManagerHelper.createRequestRoleIntent(context, SpecialAccessType.DEFAULT_SMS_APP)
                    ?: Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
            SpecialAccessType.DEFAULT_DIALER -> {
                RoleManagerHelper.createRequestRoleIntent(context, SpecialAccessType.DEFAULT_DIALER)
                    ?: Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
            SpecialAccessType.OVERLAY_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                } else {
                    getAppDetailsSettingsIntent(context)
                }
            }
            SpecialAccessType.ALL_FILES_ACCESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                } else {
                    getAppDetailsSettingsIntent(context)
                }
            }
            SpecialAccessType.BATTERY_OPTIMIZATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                } else {
                    getAppDetailsSettingsIntent(context)
                }
            }
            else -> getAppDetailsSettingsIntent(context)
        }
    }

    fun getAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun getSpecialAccessItems(context: Context): List<SpecialAccessItem> {
        val notifStatus = if (isNotificationListenerEnabled(context)) AccessStatus.GRANTED else AccessStatus.NOT_GRANTED
        val exactAlarmStatus = if (canScheduleExactAlarms(context)) AccessStatus.GRANTED else AccessStatus.NOT_GRANTED
        val usageStatus = if (hasUsageStatsPermission(context)) AccessStatus.GRANTED else AccessStatus.NOT_GRANTED
        val accessibilityStatus = if (isAccessibilityServiceEnabled(context)) AccessStatus.GRANTED else AccessStatus.NOT_GRANTED
        val assistantStatus = RoleManagerHelper.getAssistantStatus(context)
        val smsRoleStatus = RoleManagerHelper.getSmsStatus(context)
        val dialerRoleStatus = RoleManagerHelper.getDialerStatus(context)
        val overlayStatus = if (canDrawOverlays(context)) AccessStatus.GRANTED else AccessStatus.NOT_GRANTED
        val allFilesStatus = if (hasAllFilesAccess(context)) AccessStatus.GRANTED else AccessStatus.NOT_GRANTED
        val batteryStatus = if (isBatteryOptimizationIgnored(context)) AccessStatus.GRANTED else AccessStatus.NOT_GRANTED

        return listOf(
            SpecialAccessItem(
                type = SpecialAccessType.DEFAULT_ASSISTANT,
                title = "Default Digital Assistant",
                description = "Allows invoking Tavi via system power-button long-press, corner swipe, or system assist action.",
                status = assistantStatus,
                isRole = true,
                securityNotice = "Operates 100% on-device; handles system assist intents without cloud telemetry.",
                requiredApiLevel = 24
            ),
            SpecialAccessItem(
                type = SpecialAccessType.DEFAULT_SMS_APP,
                title = "Default SMS & Messaging Role",
                description = "Directly manages cellular text messages and MMS. Enables background incoming SMS processing and reply handling.",
                status = smsRoleStatus,
                isRole = true,
                securityNotice = "Safeguard: Outgoing SMS requires explicit confirmation dialog. Messages never leave device.",
                requiredApiLevel = 24
            ),
            SpecialAccessItem(
                type = SpecialAccessType.DEFAULT_DIALER,
                title = "Default Phone / Dialer Role",
                description = "Manages incoming and outgoing phone calls using Android Telecom InCallService integration.",
                status = dialerRoleStatus,
                isRole = true,
                securityNotice = "Safeguard: Outgoing phone calls require user confirmation. Call logs remain strictly private.",
                requiredApiLevel = 24
            ),
            SpecialAccessItem(
                type = SpecialAccessType.NOTIFICATION_LISTENER,
                title = "Notification Access",
                description = "Reads incoming notifications to summarize alerts and dismiss notifications upon request.",
                status = notifStatus,
                isRole = false,
                securityNotice = "Only summaries are extracted; sensitive alert content is sanitized and never transmitted remotely.",
                requiredApiLevel = 24
            ),
            SpecialAccessItem(
                type = SpecialAccessType.USAGE_STATS,
                title = "App Usage & Screen Time Access",
                description = "Queries Android UsageStatsManager for daily screen time, app foreground usage, and recently launched apps.",
                status = usageStatus,
                isRole = false,
                securityNotice = "Access is read-only. Usage statistics remain on-device for assistant summaries.",
                requiredApiLevel = 24
            ),
            SpecialAccessItem(
                type = SpecialAccessType.ACCESSIBILITY_SERVICE,
                title = "Accessibility Assistant Service",
                description = "Enables on-screen text inspection and standard UI actions (Back, Home, Recents, Notifications).",
                status = accessibilityStatus,
                isRole = false,
                securityNotice = "STRICT PRIVACY: All password fields are automatically REDACTED. Credit card and OTP numbers are masked. Never collects passwords or bypasses lock screens.",
                requiredApiLevel = 24
            ),
            SpecialAccessItem(
                type = SpecialAccessType.OVERLAY_PERMISSION,
                title = "Display Over Other Apps",
                description = "Allows displaying floating assistant controls or system-wide overlays for quick access.",
                status = overlayStatus,
                isRole = false,
                securityNotice = "Overlay displays only when explicitly invoked; no background touch interception.",
                requiredApiLevel = 23
            ),
            SpecialAccessItem(
                type = SpecialAccessType.ALL_FILES_ACCESS,
                title = "All Files Access (Broad Storage)",
                description = "System-wide storage management. NOTE: Tavi defaults to Storage Access Framework (SAF) for privacy.",
                status = allFilesStatus,
                isRole = false,
                securityNotice = "Recommended: Keep SAF. Broad storage access is only necessary if you require assistant indexing across external storage.",
                requiredApiLevel = 30
            ),
            SpecialAccessItem(
                type = SpecialAccessType.EXACT_ALARM,
                title = "Exact Alarms & Background Scheduling",
                description = "Allows triggering precision timers, reminders, and scheduled tasks at exact times.",
                status = exactAlarmStatus,
                isRole = false,
                securityNotice = "Uses least-privileged AlarmManager / WorkManager scheduling.",
                requiredApiLevel = 31
            ),
            SpecialAccessItem(
                type = SpecialAccessType.BATTERY_OPTIMIZATION,
                title = "Battery Optimization Exemption",
                description = "Prevents Android Doze mode from deferring scheduled background reminders.",
                status = batteryStatus,
                isRole = false,
                securityNotice = "Offered on an opt-in basis for essential background tasks. Not requested on startup.",
                requiredApiLevel = 23
            )
        )
    }

    fun getCapabilityGroups(context: Context): List<CapabilityGroup> {
        val hasCalendarRead = hasPermission(context, Manifest.permission.READ_CALENDAR)
        val hasCalendarWrite = hasPermission(context, Manifest.permission.WRITE_CALENDAR)
        val hasContactsRead = hasPermission(context, Manifest.permission.READ_CONTACTS)
        val hasContactsWrite = hasPermission(context, Manifest.permission.WRITE_CONTACTS)
        val hasSmsSend = hasPermission(context, Manifest.permission.SEND_SMS)
        val hasSmsRead = hasPermission(context, Manifest.permission.READ_SMS)
        val hasCallPhone = hasPermission(context, Manifest.permission.CALL_PHONE)
        val hasAudio = hasPermission(context, Manifest.permission.RECORD_AUDIO)
        val hasLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasNotifListener = hasSpecialAccess(context, SpecialAccessType.NOTIFICATION_LISTENER)
        val hasExactAlarm = hasSpecialAccess(context, SpecialAccessType.EXACT_ALARM)
        val hasUsage = hasSpecialAccess(context, SpecialAccessType.USAGE_STATS)
        val hasAccessibility = hasSpecialAccess(context, SpecialAccessType.ACCESSIBILITY_SERVICE)

        return listOf(
            CapabilityGroup(
                category = ToolCategory.USAGE,
                title = "App Usage & Screen Time",
                description = "Retrieve authorized app-usage statistics, daily foreground usage, and screen-time breakdowns.",
                requiredPermissions = emptyList(),
                specialAccessType = SpecialAccessType.USAGE_STATS,
                supportedOperations = listOf("usage_get_stats"),
                limitations = "Requires special Usage Access permission in Android Settings.",
                isGranted = hasUsage
            ),
            CapabilityGroup(
                category = ToolCategory.ACCESSIBILITY,
                title = "Accessibility & Screen Inspection",
                description = "Inspect visible on-screen text and trigger system navigation (Back, Home, Recents, Notifications).",
                requiredPermissions = emptyList(),
                specialAccessType = SpecialAccessType.ACCESSIBILITY_SERVICE,
                supportedOperations = listOf("accessibility_read_screen", "accessibility_perform_action"),
                limitations = "Requires explicitly enabling Tavi Accessibility Service. Password fields and OTP codes are strictly redacted.",
                isGranted = hasAccessibility
            ),
            CapabilityGroup(
                category = ToolCategory.CALENDAR,
                title = "Calendar & Schedule",
                description = "Query events, inspect schedules, detect conflicts, schedule and delete meetings.",
                requiredPermissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                supportedOperations = listOf("calendar_list_events", "calendar_get_event", "calendar_create_event", "calendar_update_event", "calendar_delete_event"),
                limitations = "Requires local calendar accounts; writes verified against CalendarProvider.",
                isGranted = hasCalendarRead && hasCalendarWrite
            ),
            CapabilityGroup(
                category = ToolCategory.CONTACTS,
                title = "Contacts & People",
                description = "Search people by name, phone, or email. Resolves partial names with ambiguity detection.",
                requiredPermissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                supportedOperations = listOf("contacts_search", "contacts_resolve", "contacts_create"),
                limitations = "Exposes ambiguity when multiple people match; never silently selects a contact.",
                isGranted = hasContactsRead
            ),
            CapabilityGroup(
                category = ToolCategory.NOTIFICATIONS,
                title = "Notification Manager",
                description = "Inspect and summarize active alerts from messaging and other apps.",
                requiredPermissions = emptyList(),
                specialAccessType = SpecialAccessType.NOTIFICATION_LISTENER,
                supportedOperations = listOf("notifications_read_summary", "notifications_dismiss", "notifications_open"),
                limitations = "Requires special Notification Listener access in Android Settings. Historical dismissed alerts are not stored by the OS.",
                isGranted = hasNotifListener
            ),
            CapabilityGroup(
                category = ToolCategory.TELEPHONY,
                title = "Calls & SMS Messages",
                description = "Prepare SMS drafts, send messages, search inbox, open dialer, and initiate calls.",
                requiredPermissions = listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.CALL_PHONE),
                supportedOperations = listOf("messages_draft", "messages_send_sms", "messages_search", "phone_dial", "phone_call", "call_log_search"),
                limitations = "Consequential transmission requires explicit user confirmation dialog.",
                isGranted = hasSmsSend && hasCallPhone
            ),
            CapabilityGroup(
                category = ToolCategory.CLOCK,
                title = "Alarms, Timers & Reminders",
                description = "Create system alarms, set countdown timers, schedule exact notifications, query local time.",
                requiredPermissions = listOf(Manifest.permission.SCHEDULE_EXACT_ALARM),
                specialAccessType = SpecialAccessType.EXACT_ALARM,
                supportedOperations = listOf("alarm_create", "alarm_show", "timer_set", "reminder_create", "clock_query_time"),
                limitations = "Exact alarms require Android 12+ permission; delegates to system clock app for alarms.",
                isGranted = hasExactAlarm
            ),
            CapabilityGroup(
                category = ToolCategory.APPLICATIONS,
                title = "Applications & Navigation",
                description = "List installed launchable apps, launch apps by name, open app settings, share content.",
                requiredPermissions = emptyList(),
                supportedOperations = listOf("app_list_launchable", "app_launch", "app_open_settings", "browser_open_url", "share_content"),
                limitations = "Package-visibility compliant via <queries>; respects Android platform security.",
                isGranted = true
            ),
            CapabilityGroup(
                category = ToolCategory.FILES,
                title = "Files & Documents",
                description = "Browse documents via Storage Access Framework, export notes and share files.",
                requiredPermissions = emptyList(),
                supportedOperations = listOf("files_browse_document", "files_export_note"),
                limitations = "Uses Storage Access Framework (SAF); avoids broad storage permissions.",
                isGranted = true
            ),
            CapabilityGroup(
                category = ToolCategory.DEVICE,
                title = "Device State & Settings",
                description = "Read battery level, charging status, available storage, and open specific system Settings panels.",
                requiredPermissions = emptyList(),
                supportedOperations = listOf("device_get_status", "settings_open"),
                limitations = "Read-only for state; guides users directly to exact Settings page for protected changes.",
                isGranted = true
            ),
            CapabilityGroup(
                category = ToolCategory.LOCATION,
                title = "Location & Maps",
                description = "Read current foreground location and launch navigation intents.",
                requiredPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                supportedOperations = listOf("location_get_current", "maps_navigate"),
                limitations = "Foreground location only; no background location tracking without explicit authorization.",
                isGranted = hasLocation
            ),
            CapabilityGroup(
                category = ToolCategory.MEDIA,
                title = "Camera & Media Control",
                description = "Launch camera for photos, control media playback, and trigger voice speech.",
                requiredPermissions = listOf(Manifest.permission.RECORD_AUDIO),
                supportedOperations = listOf("camera_launch", "media_control", "voice_speak"),
                limitations = "Uses standard Android intents; does not run secret background microphone/camera recording.",
                isGranted = hasAudio
            ),
            CapabilityGroup(
                category = ToolCategory.NOTES,
                title = "Notes & Clipboard Memory",
                description = "Local SQLite notes, clipboard reading/copying, and private user memory.",
                requiredPermissions = emptyList(),
                supportedOperations = listOf("note_create", "notes_list", "note_delete", "clipboard_read", "clipboard_copy", "memory_save", "memory_retrieve", "memory_delete"),
                limitations = "100% on-device SQLite database; clipboard read restricted to foreground.",
                isGranted = true
            ),
            CapabilityGroup(
                category = ToolCategory.CONNECTIVITY,
                title = "Network & Connectivity",
                description = "Inspect Wi-Fi, cellular, metered status, and network state.",
                requiredPermissions = listOf(Manifest.permission.ACCESS_NETWORK_STATE),
                supportedOperations = listOf("network_get_status"),
                limitations = "Local ConnectivityManager inspection only; no telemetry or remote connections.",
                isGranted = hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)
            )
        )
    }
}
