package com.giochiclassici.arcade.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.giochiclassici.arcade.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una release pubblicata su GitHub. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: ReleaseInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Controlla l'ultima release del repository GitHub, scarica l'APK e avvia l'installazione.
 * Lo stato è osservabile da Compose.
 */
object Updater {
    val currentVersion: String = BuildConfig.VERSION_NAME
    val repoUrl = "https://github.com/${BuildConfig.GITHUB_REPO}"
    private val apiUrl = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** Versione più recente trovata (anche se l'utente non ha ancora aperto le impostazioni). */
    val availableVersion: String?
        get() = when (val s = state) {
            is UpdateState.Available -> s.info.version
            is UpdateState.Downloading -> s.info.version
            is UpdateState.ReadyToInstall -> s.info.version
            else -> null
        }

    private var job: Job? = null
    private var checkedThisSession = false

    /** Controllo silenzioso all'avvio: una volta per sessione, gli errori non vengono mostrati. */
    fun checkOnStartup(scope: CoroutineScope) {
        if (checkedThisSession) return
        checkedThisSession = true
        check(scope, silent = true)
    }

    fun check(scope: CoroutineScope, silent: Boolean = false) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        job?.cancel()
        if (!silent) state = UpdateState.Checking
        job = scope.launch {
            state = try {
                val info = withContext(Dispatchers.IO) { fetchLatest() }
                when {
                    info == null -> if (silent) UpdateState.Idle else UpdateState.Error("Nessuna versione pubblicata su GitHub.")
                    isNewer(info.version, currentVersion) -> UpdateState.Available(info)
                    else -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Error("Impossibile contattare GitHub. Controlla la connessione e riprova.")
            }
        }
    }

    fun download(context: Context, scope: CoroutineScope) {
        val info = when (val s = state) {
            is UpdateState.Available -> s.info
            is UpdateState.Error -> return
            else -> return
        }
        val dir = File(context.cacheDir, "updates")
        job?.cancel()
        state = UpdateState.Downloading(info, 0f)
        job = scope.launch {
            state = try {
                val file = withContext(Dispatchers.IO) {
                    dir.deleteRecursively()
                    dir.mkdirs()
                    val out = File(dir, "ArcadeClassici-${info.version}.apk")
                    downloadTo(info, out) { p -> state = UpdateState.Downloading(info, p) }
                    out
                }
                UpdateState.ReadyToInstall(info, file).also { install(context, file) }
            } catch (e: Exception) {
                UpdateState.Error("Download non riuscito. Riprova.")
            }
        }
    }

    /** Avvia l'installer di sistema. Se serve, apre prima il permesso "installa app sconosciute". */
    fun install(context: Context, file: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun needsInstallPermission(context: Context) = !context.packageManager.canRequestPackageInstalls()

    fun reset() {
        if (state is UpdateState.Error || state is UpdateState.UpToDate) state = UpdateState.Idle
    }

    // ------------------------------------------------------------------------

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ArcadeClassici/$currentVersion")
        }

    private fun fetchLatest(): ReleaseInfo? {
        val conn = open(apiUrl).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return ReleaseInfo(
                        version = json.getString("tag_name").removePrefix("v"),
                        notes = json.optString("body").substringBefore("\n---").trim(),
                        apkUrl = a.getString("browser_download_url"),
                        size = a.optLong("size"),
                        pageUrl = json.optString("html_url"),
                    )
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadTo(info: ReleaseInfo, out: File, onProgress: (Float) -> Unit) {
        val conn = open(info.apkUrl)
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
            var read = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - lastReport > 128 * 1024) {
                            lastReport = read
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }

    /** Confronta due versioni "1.2.3": true se [remote] è più recente di [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
