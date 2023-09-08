package org.jetbrains.sbt.project.template.techhub

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import com.intellij.util.net.NetUtils
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.ScalaBundle

import java.io.{BufferedOutputStream, FileOutputStream, IOException, OutputStream}
import java.util
import java.util.concurrent.Callable
import scala.util.Using
object TechHubDownloadUtil {

  private val CONTENT_LENGTH_TEMPLATE: String = "${content-length}"

  def downloadContentToFile(url: String, outputFile: File): Unit = {
    val parentDirExists: Boolean = FileUtil.createParentDirs(outputFile)
    if (!parentDirExists) throw new IOException(s"Parent dir of '${outputFile.getAbsolutePath}' could not be created!")

    Using.resource(new BufferedOutputStream(new FileOutputStream(outputFile))) { out =>
      download(None, url, out)
    }
  }

  private def download(progress: Option[ProgressIndicator], location: String, output: OutputStream): Unit = {
    val originalText: String = progress.map(_.getText).getOrElse("")
    substituteContentLength(progress, originalText, -1)
    progress.foreach(p => p.setText2(ScalaBundle.message("downloading.location", location)))

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
