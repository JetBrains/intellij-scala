package org.jetbrains.sbt.project.template.activator

import java.io._

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.net.NetUtils

/**
 * User: Dmitry.Naydanov
 * Date: 30.01.15.
 */
object ActivatorDownloadUtil {
  private val CONTENT_LENGTH_TEMPLATE: String = "${content-length}"

  def downloadContentToFile(progress: ProgressIndicator,url: String,outputFile: File) {
    val parentDirExists: Boolean = FileUtil.createParentDirs(outputFile)
    if (!parentDirExists) throw new IOException("Parent dir of '" + outputFile.getAbsolutePath + "' can not be created!")

    val out = new BufferedOutputStream(new FileOutputStream(outputFile))
    try {
      download(progress, url, out)
    } finally out.close()
  }

  def download(progress: ProgressIndicator, location: String, output: OutputStream) {
    val originalText: String = if (progress != null) progress.getText else null
    substituteContentLength(progress, originalText, -1)
    if (progress != null) progress.setText2("Downloading " + location)

    try {
      HttpRequests.request(location).productNameAsUserAgent.connect(new HttpRequests.RequestProcessor[Object]() {
        def process(request: HttpRequests.Request): AnyRef = {
          try {
            val contentLength: Int = request.getConnection.getContentLength
            substituteContentLength(progress, originalText, contentLength)
            NetUtils.copyStreamContent(progress, request.getInputStream, output, contentLength)
          }
          catch {
            case e: IOException =>
              throw new IOException(HttpRequests.createErrorMessage(e, request, true), e)
          }

          null
        }
      })
    } catch {
      case e: IOException => throw new IOException("Cannot download " + location, e)
    }
  }

  private def substituteContentLength(progress: ProgressIndicator, text: String, contentLengthInBytes: Int) {
    if (progress == null || text == null) return

    val ind = text indexOf CONTENT_LENGTH_TEMPLATE

    if (ind != -1) {
      val mes: String = formatContentLength(contentLengthInBytes)
      val newText: String = text.substring(0, ind) + mes + text.substring(ind + CONTENT_LENGTH_TEMPLATE.length)
      progress.setText(newText)
    }
  }

  private def formatContentLength(contentLengthInBytes: Int): String = {
    if (contentLengthInBytes < 0) return ""

    val kilo: Int = 1024

    if (contentLengthInBytes < kilo) return f", $contentLengthInBytes bytes"

    if (contentLengthInBytes < kilo * kilo) return f", ${contentLengthInBytes / (1.0 * kilo)}%.1f KB"

    f", ${contentLengthInBytes / (1.0 * kilo * kilo)}%.1f MB"
  }
}
