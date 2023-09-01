package org.jetbrains.plugins.scala.util

import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.plugins.scala.ScalaBundle

import java.io.IOException
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.io.Source
import scala.util.Try

object HttpDownloadUtil {

  @RequiresBackgroundThread
  def downloadString(url: String, timeoutMs: FiniteDuration, canBeCanceled: Boolean, indicatorOpt: Option[ProgressIndicator]): Try[String] =
    loadLinesFrom(url, canBeCanceled, indicatorOpt, timeoutMs).map(_.mkString)

  /**
   * If canBeCanceled is true and this method is invoking in a different thread than the one in which ProgressIndicator was created remember to pass
   * ProgressIndicator as a parameter because [[com.intellij.openapi.progress.ProgressManager.checkCanceled]] may not work as expected
   */
  def loadLinesFrom(url: String, canBeCanceled: Boolean, indicatorOpt: Option[ProgressIndicator], timeout: FiniteDuration = 10.seconds): Try[Seq[String]] =
    Try(HttpConfigurable.getInstance().openHttpConnection(url)).map { connection =>
      try {
        connection.setConnectTimeout(timeout.toMillis.toInt)
        connection.setReadTimeout(timeout.toMillis.toInt)
        val responseCode = connection.getResponseCode
        if (responseCode == -1) {
          throw new IOException(ScalaBundle.message("no.response.status.from.connection.to.url", url))
        } else if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
          Source.fromInputStream(connection.getInputStream).getLines()
            .map { line =>
              if (canBeCanceled) performCheckCanceled(indicatorOpt)
              line
            }.toVector
        } else {
          throw new IOException(ScalaBundle.message("response.to.connection.to.url.was.code", url, responseCode))
        }
      } finally {
        connection.disconnect()
      }
    }

  private def performCheckCanceled(indicatorOpt: Option[ProgressIndicator] = None): Unit =
    indicatorOpt match {
      case Some(indicator) => indicator.checkCanceled()
      case None => ProgressManager.checkCanceled()
    }

}
