package dev.ykro.recoverypal.agent

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Supplies the `.litertlm` weights for private mode. LiteRT-LM only takes a file path, so the app
 * downloads the model once (Wi-Fi) or you push it with adb; a pushed file wins over the download.
 */
object ModelStore {
  private const val REPO = "litert-community/gemma-4-E2B-it-litert-lm"
  const val FILE_NAME = "gemma-4-E2B-it.litertlm"
  const val SIZE_LABEL = "2.6 GB"
  private const val PART = ".part"

  fun directory(context: Context): File = (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }

  fun find(context: Context): File? =
    directory(context).listFiles { f -> f.isFile && f.name.endsWith(".litertlm") }.orEmpty().sortedBy { it.name }.let { list ->
      list.firstOrNull { it.name != FILE_NAME } ?: list.firstOrNull()
    }

  fun pushCommand(context: Context): String = "adb push $FILE_NAME ${directory(context).absolutePath}/"

  fun download(context: Context): Flow<Float> =
    flow {
        emit(0f)
        val partial = File(directory(context), FILE_NAME + PART)
        try {
          val conn = URL("https://huggingface.co/$REPO/resolve/main/$FILE_NAME").openConnection() as HttpURLConnection
          conn.connectTimeout = 30_000
          conn.readTimeout = 30_000
          try {
            check(conn.responseCode == HttpURLConnection.HTTP_OK) { "Model download failed with HTTP ${conn.responseCode}." }
            val total = conn.contentLengthLong
            val step = if (total > 0) total / 200 else Long.MAX_VALUE
            var copied = 0L
            var reported = 0L
            conn.inputStream.use { input ->
              partial.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                while (true) {
                  coroutineContext.ensureActive()
                  val read = input.read(buffer)
                  if (read < 0) break
                  output.write(buffer, 0, read)
                  copied += read
                  if (copied - reported >= step) {
                    reported = copied
                    emit(copied.toFloat() / total)
                  }
                }
              }
            }
          } finally {
            conn.disconnect()
          }
          check(partial.renameTo(File(directory(context), FILE_NAME))) { "Downloaded model could not be moved into place." }
        } catch (t: Throwable) {
          partial.delete()
          throw t
        }
        emit(1f)
      }
      .flowOn(Dispatchers.IO)

  fun delete(context: Context) {
    directory(context).listFiles { f -> f.name.endsWith(".litertlm") }?.forEach { it.delete() }
  }
}
