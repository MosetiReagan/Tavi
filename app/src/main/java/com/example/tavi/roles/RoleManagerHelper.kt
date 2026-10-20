package com.example.tavi.roles

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.example.tavi.model.AccessStatus
import com.example.tavi.model.SpecialAccessType

object RoleManagerHelper {

    fun getRoleStatus(context: Context, type: SpecialAccessType): AccessStatus {
        return when (type) {
            SpecialAccessType.DEFAULT_ASSISTANT -> getAssistantStatus(context)
            SpecialAccessType.DEFAULT_SMS_APP -> getSmsStatus(context)
            SpecialAccessType.DEFAULT_DIALER -> getDialerStatus(context)
            else -> AccessStatus.UNSUPPORTED
        }
    }

    fun getAssistantStatus(context: Context): AccessStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                return if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                    AccessStatus.ROLE_HELD
                } else {
                    AccessStatus.ROLE_AVAILABLE
                }
            }
        }

        // Fallback check via Settings.Secure
        val currentAssistant = Settings.Secure.getString(context.contentResolver, "assistant")
        return if (currentAssistant != null && currentAssistant.contains(context.packageName)) {
            AccessStatus.ROLE_HELD
        } else {
            AccessStatus.ROLE_AVAILABLE
        }
    }

    fun getSmsStatus(context: Context): AccessStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                return if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    AccessStatus.ROLE_HELD
                } else {
                    AccessStatus.ROLE_AVAILABLE
                }
            }
        }

        val defaultSms = Telephony.Sms.getDefaultSmsPackage(context)
        return when {
            defaultSms == context.packageName -> AccessStatus.ROLE_HELD
            defaultSms != null -> AccessStatus.ROLE_AVAILABLE
            else -> AccessStatus.ROLE_UNAVAILABLE
        }
    }

    fun getDialerStatus(context: Context): AccessStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                return if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    AccessStatus.ROLE_HELD
                } else {
                    AccessStatus.ROLE_AVAILABLE
                }
            }
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val defaultDialer = telecomManager?.defaultDialerPackage
        return when {
            defaultDialer == context.packageName -> AccessStatus.ROLE_HELD
            defaultDialer != null -> AccessStatus.ROLE_AVAILABLE
            else -> AccessStatus.ROLE_UNAVAILABLE
        }
    }

    fun createRequestRoleIntent(context: Context, type: SpecialAccessType): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            val roleName = when (type) {
                SpecialAccessType.DEFAULT_ASSISTANT -> RoleManager.ROLE_ASSISTANT
                SpecialAccessType.DEFAULT_SMS_APP -> RoleManager.ROLE_SMS
                SpecialAccessType.DEFAULT_DIALER -> RoleManager.ROLE_DIALER
                else -> null
            }
            if (roleManager != null && roleName != null && roleManager.isRoleAvailable(roleName)) {
                return roleManager.createRequestRoleIntent(roleName)
            }
        }

        // Fallbacks for older Android or direct settings intent
        return when (type) {
            SpecialAccessType.DEFAULT_ASSISTANT -> Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            SpecialAccessType.DEFAULT_SMS_APP -> {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
            }
            SpecialAccessType.DEFAULT_DIALER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                        putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                }
            }
            else -> null
        }
    }
}
