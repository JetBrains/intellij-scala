package org.jetbrains.plugins.scala.project.sdkdetect.repository

import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path}
import scala.util.Using

private object DownloadUtil {
  case class RetryConfig(maxAttempts: Int = 3, delayMs: Int = 500)
  case class DownloadState(bytesDownloaded: Long = 0)

  /**
   * Downloads a file from the given URL to the specified directory.
   *
   * @param urlString The URL to download from
   * @param targetDir The directory to save the file to
   * @param fileName  Optional specific filename to use; if not provided, extracts from URL
   * @return The downloaded file
   * @note semi-AI-generated
   */
  def downloadFile(
    urlString: String,
    targetFilePath: Path,
    retryConfig: RetryConfig = RetryConfig()
  ): Unit = {
    //noinspection NoTailRecursionAnnotation
    def downloadWithRetry(attempt: Int = 1, state: DownloadState = DownloadState()): Unit = {
      try {
        downloadFileImpl(urlString, targetFilePath, state)
      } catch {
        case e@(_: java.net.SocketTimeoutException | _: java.net.ConnectException | _: java.io.IOException) =>
          if (attempt < retryConfig.maxAttempts) {
            println(s"Download attempt $attempt failed: ${e.getMessage}. Retrying in ${retryConfig.delayMs}ms...")
            Thread.sleep(retryConfig.delayMs)
            downloadWithRetry(attempt + 1, state)
          } else {
            throw new RuntimeException(s"Failed to download after ${retryConfig.maxAttempts} attempts", e)
          }
      }
    }

    downloadWithRetry()
  }

  private def downloadFileImpl(
    urlString: String,
    targetFilePath: Path,
    state: DownloadState
  ): Unit = {
    println(s"Downloading file from $urlString to $targetFilePath")

    val url = new URL(urlString)
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestMethod("GET")
    if (state.bytesDownloaded > 0) {
      connection.setRequestProperty("Range", s"bytes=${state.bytesDownloaded}-")
    }

    Using.resource(connection.getInputStream) { inputStream =>
      Using.resource(Files.newOutputStream(targetFilePath, java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.WRITE,
        if (state.bytesDownloaded > 0) java.nio.file.StandardOpenOption.APPEND else java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
      )) { outputStream =>
        val buffer = new Array[Byte](8192)
        var bytesRead = 0
        var totalBytesDownloaded = state.bytesDownloaded
        while ( {
          bytesRead = inputStream.read(buffer);
          bytesRead != -1
        }) {
          outputStream.write(buffer, 0, bytesRead)
          totalBytesDownloaded += bytesRead
        }
      }
    }
  }
}
