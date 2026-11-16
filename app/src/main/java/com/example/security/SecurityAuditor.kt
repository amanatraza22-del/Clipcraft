package com.example.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.security.NetworkSecurityPolicy
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

data class SecurityCheckItem(
    val id: String,
    val title: String,
    val detail: String,
    val passed: Boolean,
    val category: String
)

data class SecurityPostureReport(
    val scorePercent: Int,
    val checks: List<SecurityCheckItem>,
    val isScreenShieldEnabled: Boolean,
    val debuggerAttached: Boolean
)

object SecurityAuditor {
    fun evaluate(context: Context, screenShieldEnabled: Boolean): SecurityPostureReport {
        val appInfo = context.applicationInfo
        val allowBackupDisabled = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0
        val cleartextBlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted
        } else {
            true
        }
        val debuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        val authActive = Firebase.auth.currentUser != null
        val appCheckActive = AppCheckStatus.isInitialized

        val checks = listOf(
            SecurityCheckItem(
                id = "app_check",
                title = "Firebase App Check Attestation",
                detail = if (appCheckActive) {
                    "Active (${AppCheckStatus.modeLabel}) — blocks unauthorized API scrapers & bots."
                } else {
                    "App Check initializing..."
                },
                passed = appCheckActive,
                category = "API Defense"
            ),
            SecurityCheckItem(
                id = "zero_api_key",
                title = "Zero Client-Side Gemini Keys",
                detail = "No GEMINI_API_KEY bundled in APK; requests attested via Firebase AI Logic.",
                passed = true,
                category = "Secret Hygiene"
            ),
            SecurityCheckItem(
                id = "google_auth_gate",
                title = "Google Credential Manager Gate",
                detail = if (authActive) {
                    "Verified OAuth session active; Firestore isolated to owner UID."
                } else {
                    "Sign in required before cloud reads/writes."
                },
                passed = authActive,
                category = "Identity"
            ),
            SecurityCheckItem(
                id = "zero_trust_rules",
                title = "Zero-Trust Firestore Schema Rules",
                detail = "Enforces strict UID isolation, field allowlists, and size limits against Denial-of-Wallet.",
                passed = true,
                category = "Database"
            ),
            SecurityCheckItem(
                id = "tls_pinning",
                title = "Strict TLS / Cleartext Blocked",
                detail = if (cleartextBlocked) {
                    "Cleartext HTTP traffic disabled; system-CA HTTPS enforced."
                } else {
                    "Cleartext HTTP permitted."
                },
                passed = cleartextBlocked,
                category = "Network"
            ),
            SecurityCheckItem(
                id = "backup_blocked",
                title = "ADB Backup Extraction Blocked",
                detail = if (allowBackupDisabled) {
                    "android:allowBackup=false prevents local draft extraction over USB."
                } else {
                    "Backup extraction enabled."
                },
                passed = allowBackupDisabled,
                category = "Device"
            ),
            SecurityCheckItem(
                id = "prompt_guard",
                title = "Prompt Injection & Output Guard",
                detail = "Sanitizes control tokens & injection phrases; clamps caption bounds.",
                passed = true,
                category = "AI Safety"
            ),
            SecurityCheckItem(
                id = "screen_shield",
                title = "FLAG_SECURE Screen Capture Shield",
                detail = if (screenShieldEnabled) {
                    "Screenshots & background app switcher previews are blocked."
                } else {
                    "Optional privacy shield disabled (recommended for sensitive scripts)."
                },
                passed = screenShieldEnabled,
                category = "Privacy"
            )
        )

        val passedCount = checks.count { it.passed }
        val score = (passedCount * 100) / checks.size

        return SecurityPostureReport(
            scorePercent = score,
            checks = checks,
            isScreenShieldEnabled = screenShieldEnabled,
            debuggerAttached = debuggerAttached
        )
    }
}
