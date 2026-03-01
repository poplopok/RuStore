package com.example.rustorecatalog.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class PackageInstallerResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(EXTRA_APP_ID).orEmpty()
        if (appId.isBlank()) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        val action = intent.action
        android.util.Log.d(
            "PackageInstaller",
            "onReceive action=$action status=$status message=$message extras=${intent.extras}"
        )

        when (action) {
            ACTION_INSTALL_COMMIT -> {
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        InstallManager.updateState(appId, InstallState.Installed)
                    }
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirmIntent != null) {
                            confirmIntent.putExtra(EXTRA_APP_ID, appId)
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(confirmIntent)
                        }
                    }
                    else -> {
                        val fallback = if (status == PackageInstaller.STATUS_FAILURE) {
                            "Не удалось установить приложение (код: 1).\n" +
                                "Что сделать:\n" +
                                "1) Удалите это приложение (если стоит версия из Google Play/другого источника).\n" +
                                "2) Разрешите установку из этого источника в настройках Android.\n" +
                                "3) Попробуйте установить снова.\n" +
                                "Если не поможет — APK, скорее всего, несовместим с вашим устройством."
                        } else {
                            "Не удалось установить приложение (код: $status). Проверьте разрешение на установку и удалите установленную версию, затем повторите."
                        }
                        InstallManager.updateState(
                            appId,
                            InstallState.Error(message ?: fallback)
                        )
                    }
                }
            }

            ACTION_UNINSTALL_COMMIT -> {
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        InstallManager.updateState(appId, InstallState.NotInstalled)
                    }
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirmIntent != null) {
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(confirmIntent)
                        }
                    }
                    else -> {
                        InstallManager.updateState(
                            appId,
                            InstallState.Error(message ?: "Не удалось удалить приложение")
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_APP_ID = "extra_app_id"
        const val ACTION_INSTALL_COMMIT = "com.example.rustorecatalog.install.ACTION_INSTALL_COMMIT"
        const val ACTION_UNINSTALL_COMMIT = "com.example.rustorecatalog.install.ACTION_UNINSTALL_COMMIT"
    }
}
