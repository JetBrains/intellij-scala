package org.jetbrains.sbt.project.template.techhub

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.{FileUtil, StreamUtil}
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.net.{HttpConfigurable, NetUtils}
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.sbt.SbtBundle

import java.io._
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.Callable
import scala.annotation.nowarn
import scala.util.{Failure, Try}

object TechHubDownloadUtil {
  private val CONTENT_LENGTH_TEMPLATE: String = "${content-length}"

  def downloadContentToFile(url: String, outputFile: File): Unit = {
    val parentDirExists: Boolean = FileUtil.createParentDirs(outputFile)
    if (!parentDirExists) throw new IOException(s"Parent dir of '${outputFile.getAbsolutePath}' could not be created!")

    val out = new BufferedOutputStream(new FileOutputStream(outputFile))
    try {
      download(None, url, out)
    } finally out.close()
  }

  def download(progress: Option[ProgressIndicator], location: String, output: OutputStream): Unit = {
    val originalText: String = progress.map(_.getText).getOrElse("")
    substituteContentLength(progress, originalText, -1)
    progress.foreach(p => p.setText2(SbtBundle.message("sbt.techhub.downloading.location", location)))

    try {
      PooledThreadExecutor.INSTANCE.invokeAny(util.Arrays.asList(new Callable[Object] {
        override def call(): Object = HttpRequests.request(location).productNameAsUserAgent.connect((request: HttpRequests.Request) => {
          try {
            val contentLength: Int = request.getConnection.getContentLength
            substituteContentLength(progress, originalText, contentLength)
            NetUtils.copyStreamContent(progress.orNull, request.getInputStream, output, contentLength.toLong)
          }
          catch {
            case e: IOException =>
              throw new IOException(HttpRequests.createErrorMessage(e, request, true), e)
          }

          null
        })
      }))
    } catch {
      case e: IOException => throw new IOException("Cannot download " + location, e)
    }
  }

  @RequiresBackgroundThread
  def downloadString(url: String, timeoutMs: Int): Try[String] = {
    val conf = HttpConfigurable.getInstance()
    var connection: HttpURLConnection = null

    try {
      connection = conf.openHttpConnection(url)
      connection.setConnectTimeout(timeoutMs)
      connection.connect()

      val status = connection.getResponseMessage

      if (status == null)
        Failure(new IOException(SbtBundle.message("sbt.techhub.no.response.status.from.connection.to.url", url)))
      else if (status.trim.startsWith("OK"))
        Try(StreamUtil.readText(connection.getInputStream, StandardCharsets.UTF_8): @nowarn("cat=deprecation"))
      else
        Failure(new IOException(SbtBundle.message("sbt.techhub.response.to.connection.to.url.was.status", url, status)))
    } finally {
      if (connection != null) {
        connection.disconnect()
      }
    }
  }

  private def substituteContentLength(progress: Option[ProgressIndicator], text: String, contentLengthInBytes: Int): Unit = {
    progress.foreach { prog =>
      val ind = text indexOf CONTENT_LENGTH_TEMPLATE

      if (ind != -1) {
        val mes: String = formatContentLength(contentLengthInBytes)
        val newText: String = text.substring(0, ind) + mes + text.substring(ind + CONTENT_LENGTH_TEMPLATE.length)
        prog.setText(newText)
      }
    }
  }

  private def formatContentLength(contentLengthInBytes: Int): String = {
    val kilo: Int = 1024
    if (contentLengthInBytes < 0)
      ""
    else if (contentLengthInBytes < kilo)
      f", $contentLengthInBytes bytes"
    else if (contentLengthInBytes < kilo * kilo)
      f", ${contentLengthInBytes / (1.0 * kilo)}%.1f KB"
    else
      f", ${contentLengthInBytes / (1.0 * kilo * kilo)}%.1f MB"
  }
}
