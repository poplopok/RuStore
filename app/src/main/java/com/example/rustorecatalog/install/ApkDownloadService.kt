package com.example.rustorecatalog.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.app.NotificationCompat
import com.example.rustorecatalog.R
import com.example.rustorecatalog.network.BackendModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile
import okhttp3.Request

class ApkDownloadService : Service() {
    private val logTag = "RuStoreInstall"

    override fun onBind(intent: Intent?): IBinder? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var lastNotifyTime = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appId = intent?.getStringExtra(EXTRA_APP_ID).orEmpty()
        val apkUrl = intent?.getStringExtra(EXTRA_APK_URL)
        val expectedPackageName = intent?.getStringExtra(EXTRA_EXPECTED_PACKAGE_NAME)

        if (appId.isBlank() || apkUrl.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification(progress = 0, indeterminate = true)
        startForeground(NOTIFICATION_ID, notification)

        scope.launch {
            try {
                downloadAndInstallApk(appId, apkUrl, expectedPackageName, startId)
            } catch (t: Throwable) {
                InstallManager.updateState(
                    appId,
                    InstallState.Error(t.message ?: "Ошибка загрузки")
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun getManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun downloadAndInstallApk(
        appId: String,
        apkUrl: String,
        expectedPackageName: String?,
        startId: Int
    ) {
        Log.i(logTag, "install-start appId=$appId url=$apkUrl expectedPkg=$expectedPackageName")
        InstallManager.updateState(appId, InstallState.Downloading(progress = 0))

        val apkFile = File(cacheDir, "download_$appId.apk")
        var currentUrl = apkUrl
        var redirectCount = 0
        val maxRedirects = 5
        var downloadCompleted = false

        while (true) {
            val request = Request.Builder().url(currentUrl).get().build()
            val response = BackendModule.sharedHttpClient.newCall(request).execute()
            response.use { resp ->
                val code = resp.code
                if (code in 300..399) {
                    val location = resp.header("Location")
                        ?: throw IllegalStateException("Redirect without Location header")
                    val resolved = resp.request.url.resolve(location)?.toString()
                        ?: throw IllegalStateException("Invalid redirect target: $location")
                    currentUrl = resolved
                    redirectCount++
                    if (redirectCount > maxRedirects) {
                        throw IllegalStateException("Too many redirects ($maxRedirects)")
                    }
                    return@use
                }
                if (!resp.isSuccessful) {
                    val details = resp.body?.string()?.trim()?.replace('\n', ' ')?.take(180)
                    val suffix = if (!details.isNullOrBlank()) " ($details)" else ""
                    Log.e(logTag, "download-http-fail appId=$appId code=$code msg=${resp.message} details=$details")
                    throw IllegalStateException("HTTP $code: ${resp.message}$suffix")
                }

                val body = resp.body ?: throw IllegalStateException("Empty response body")
                val totalLength = body.contentLength()
                var downloaded: Long = 0
                var lastProgress = 0

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (totalLength > 0) {
                                val progress = ((downloaded * 100) / totalLength).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress.coerceIn(0, 100)
                                    InstallManager.updateState(
                                        appId,
                                        InstallState.Downloading(progress = lastProgress)
                                    )
                                    if (System.currentTimeMillis() - lastNotifyTime > 500) {
                                        lastNotifyTime = System.currentTimeMillis()
                                        val updated = buildNotification(
                                            progress = lastProgress,
                                            indeterminate = false
                                        )
                                        getManager().notify(NOTIFICATION_ID, updated)
                                    }
                                }
                            }
                        }
                    }
                }

                if (totalLength > 0 && downloaded < totalLength) {
                    throw IllegalStateException("Downloaded $downloaded of $totalLength bytes")
                }
                downloadCompleted = true
            }
            if (downloadCompleted) break
        }
        if (!downloadCompleted) {
            Log.e(logTag, "download-failed appId=$appId reason=not-completed")
            throw IllegalStateException("Failed to download APK")
        }

        InstallManager.updateState(appId, InstallState.Downloaded)
        Log.i(logTag, "download-finished appId=$appId file=${apkFile.absolutePath} size=${apkFile.length()}")
        FileInputStream(apkFile).use { input ->
            val header = ByteArray(2)
            val read = input.read(header)
            if (read == 2) {
                if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                    throw IllegalStateException("Downloaded file is not an APK")
                }
            }
        }
        validateApkInstallability(apkFile, expectedPackageName)
        Log.i(logTag, "validate-finished appId=$appId")

        val openedSystemInstaller = installWithSystemInstallerIntent(appId, apkFile, startId)
        if (!openedSystemInstaller) {
            Log.w(logTag, "system-installer-open-failed appId=$appId fallback=packageInstaller")
            installWithPackageInstaller(appId, apkFile, startId)
        }
    }

    private fun validateApkInstallability(apkFile: File, expectedPackageName: String?) {
        val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archiveInfo = getPackageArchiveInfoCompat(apkFile.absolutePath, archiveFlags)
            ?: throw IllegalStateException("Downloaded file is not a valid APK package")

        val apkAbis = mutableSetOf<String>()
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (!name.startsWith("lib/")) continue
                val parts = name.split('/')
                if (parts.size >= 3) {
                    apkAbis.add(parts[1])
                }
            }
        }

        if (apkAbis.isNotEmpty()) {
            val deviceAbis = Build.SUPPORTED_ABIS.toSet()
            val matches = apkAbis.intersect(deviceAbis)
            if (matches.isEmpty()) {
                throw IllegalStateException(
                    "APK ABI mismatch. Device ABIs: ${deviceAbis.joinToString()}, APK ABIs: ${apkAbis.joinToString()}"
                )
            }
        }

        val actualPackageName = archiveInfo.packageName
        if (actualPackageName.isNullOrBlank()) {
            throw IllegalStateException("Downloaded APK has no package name")
        }

        if (!expectedPackageName.isNullOrBlank() && actualPackageName != expectedPackageName) {
            throw IllegalStateException(
                "Downloaded APK package mismatch. Expected: $expectedPackageName, actual: $actualPackageName"
            )
        }

        val installedInfo = runCatching {
            getInstalledPackageInfoCompat(actualPackageName, archiveFlags)
        }.getOrNull()

        if (installedInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val archiveSigners = archiveInfo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val installedSigners = installedInfo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            if (archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() && archiveSigners != installedSigners) {
                throw IllegalStateException(
                    "Конфликт подписи APK с уже установленной версией. Удалите установленное приложение и повторите установку."
                )
            }
        }

        if (installedInfo != null) {
            val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedInfo.longVersionCode
            } else {
                installedInfo.versionCode.toLong()
            }
            val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                archiveInfo.versionCode.toLong()
            }
            if (archiveVersion < installedVersion) {
                throw IllegalStateException(
                    "APK старее установленной версии (downgrade запрещен). Удалите текущую версию приложения и установите APK заново."
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageArchiveInfoCompat(apkPath: String, flags: Int): android.content.pm.PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getPackageArchiveInfo(apkPath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfoCompat(packageName: String, flags: Int): android.content.pm.PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    private fun installWithPackageInstaller(appId: String, apkFile: File, startId: Int) {
        InstallManager.updateState(appId, InstallState.Installing)
        Log.i(logTag, "package-installer-commit appId=$appId file=${apkFile.absolutePath}")

        val packageInstaller = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        FileInputStream(apkFile).use { input ->
            session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val c = input.read(buffer)
                    if (c == -1) break
                    out.write(buffer, 0, c)
                }
                session.fsync(out)
            }
        }

        val intent = Intent(this, PackageInstallerResultReceiver::class.java).apply {
            action = PackageInstallerResultReceiver.ACTION_INSTALL_COMMIT
            putExtra(PackageInstallerResultReceiver.EXTRA_APP_ID, appId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        session.commit(pendingIntent.intentSender)
        session.close()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun installWithSystemInstallerIntent(appId: String, apkFile: File, startId: Int): Boolean {
        InstallManager.updateState(appId, InstallState.Installing)
        val authority = "${applicationContext.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(this, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val legacyIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val opened = runCatching {
            startActivity(installIntent)
            true
        }.recoverCatching {
            startActivity(legacyIntent)
            true
        }.getOrElse {
            Log.e(logTag, "system-installer-start-failed appId=$appId error=${it.message}", it)
            false
        }
        if (opened) {
            Log.i(logTag, "system-installer-opened appId=$appId uri=$uri")
            InstallManager.updateState(appId, InstallState.NotInstalled)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return opened
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_install_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.channel_install_description)
            getManager().createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        progress: Int,
        indeterminate: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.channel_install_name))
            .setContentText(getString(R.string.channel_install_description))
            .setSmallIcon(R.drawable.ic_rustore_logo)
            .setOngoing(true)

        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        }

        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "apk_install_channel"
        private const val NOTIFICATION_ID = 1001

        private const val EXTRA_APP_ID = "extra_app_id"
        private const val EXTRA_APK_URL = "extra_apk_url"
        private const val EXTRA_EXPECTED_PACKAGE_NAME = "extra_expected_package_name"

        fun start(context: Context, appId: String, apkUrl: String, expectedPackageName: String?) {
            val intent = Intent(context, ApkDownloadService::class.java).apply {
                putExtra(EXTRA_APP_ID, appId)
                putExtra(EXTRA_APK_URL, apkUrl)
                putExtra(EXTRA_EXPECTED_PACKAGE_NAME, expectedPackageName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun uninstall(context: Context, appId: String, packageName: String) {
            InstallManager.updateState(appId, InstallState.Uninstalling)
            val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(uninstallIntent)
            }.onSuccess { return }

            val legacyIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(legacyIntent)
            }.onSuccess { return }

            runCatching {
                val intent = Intent(context, PackageInstallerResultReceiver::class.java).apply {
                    action = PackageInstallerResultReceiver.ACTION_UNINSTALL_COMMIT
                    putExtra(PackageInstallerResultReceiver.EXTRA_APP_ID, appId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    packageName.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                context.packageManager.packageInstaller.uninstall(packageName, pendingIntent.intentSender)
            }.onFailure {
                val detailsIntent = Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(detailsIntent) }
                InstallManager.updateState(
                    appId,
                    InstallState.Error("Не удалось открыть удаление. Откройте экран приложения и удалите вручную.")
                )
            }
        }
    }
}
