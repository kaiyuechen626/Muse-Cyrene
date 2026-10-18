// Muse Cyrene second-pass modification, 2026-10-18; GPL-3.0-or-later.
package io.zer0.muse.tools.system

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.Signature
import android.os.Build
import io.zer0.common.Logger
import java.io.File

/** Exact current certificates only: no acceptance of signing history/rotation. */
internal object AccessibilityProviderTrust {
    private const val TAG = "A11yProviderTrust"
    private val permission: String
        get() = if (io.zer0.muse.BuildConfig.CYRENE_BUILD) {
            "io.zer0.muse.cyrene.permission.A11Y_BRIDGE"
        } else {
            "io.zer0.muse.permission.A11Y_BRIDGE"
        }

    @Suppress("DEPRECATION")
    private fun flags(): Int = PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS or
        (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)

    @Suppress("DEPRECATION")
    private fun currentSigners(info: PackageInfo): Set<Signature> =
        if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        } else {
            info.signatures?.toSet().orEmpty()
        }

    private fun identityTrusted(context: Context, helper: PackageInfo): Boolean {
        if (helper.packageName != AccessibilityClient.PROVIDER_PACKAGE || helper.applicationInfo?.enabled != true) return false
        @Suppress("DEPRECATION")
        val main = context.packageManager.getPackageInfo(context.packageName, flags())
        val signers = currentSigners(helper)
        if (signers.isEmpty() || signers != currentSigners(main)) return false
        val bridge = helper.services?.singleOrNull {
            it.name == AccessibilityClient.PROVIDER_BRIDGE_CLASS
        } ?: return false
        if (bridge.packageName != helper.packageName || !bridge.exported || !bridge.enabled ||
            bridge.permission != permission
        ) return false
        val declared = helper.permissions?.singleOrNull { it.name == permission } ?: return false
        return declared.packageName == helper.packageName &&
            declared.protectionLevel == PermissionInfo.PROTECTION_SIGNATURE
    }

    @Suppress("DEPRECATION")
    fun installedTrusted(context: Context): Boolean = try {
        val pm = context.packageManager
        val helper = pm.getPackageInfo(AccessibilityClient.PROVIDER_PACKAGE, flags())
        val owner = pm.getPermissionInfo(permission, 0)
        identityTrusted(context, helper) && owner.packageName == helper.packageName &&
            owner.protectionLevel == PermissionInfo.PROTECTION_SIGNATURE &&
            pm.checkPermission(permission, context.packageName) == PackageManager.PERMISSION_GRANTED
    } catch (error: PackageManager.NameNotFoundException) {
        Logger.w(TAG, "Provider or bridge permission missing", error)
        false
    } catch (error: SecurityException) {
        Logger.w(TAG, "Provider identity inspection denied", error)
        false
    }

    @Suppress("DEPRECATION")
    fun archiveTrusted(context: Context, archive: File): Boolean {
        val helper = context.packageManager.getPackageArchiveInfo(archive.absolutePath, flags()) ?: return false
        // The permission grant may not exist until installation; inspect its declaration here.
        return identityTrusted(context, helper)
    }
}
