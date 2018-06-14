package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file.Paths

import ch.epfl.scala.bsp.Uri
import com.intellij.openapi.diagnostic.Logger
import scribe.{LogRecord, LoggerSupport}

import scala.meta.jsonrpc.Response

object BspUtil {

  implicit class BspUriOps(bspUri: Uri) {
    def toURI: URI = new URI(bspUri.value)
    def toFile: File = Paths.get(bspUri.toURI.getPath).toFile
  }

  implicit class JsonRpcResponseErrorOps(err: Response.Error) {
    def toBspError: BspError = {
      BspErrorMessage(s"bsp protocol error (${err.error.code}): ${err.error.message}")
    }
  }

  implicit class IdeaLoggerOps(ideaLogger: Logger) {
    def toScribeLogger = new IdeaLoggerSupport(ideaLogger)
  }

  class IdeaLoggerSupport(ideaLogger: Logger) extends LoggerSupport {
    override def log[M](record: LogRecord[M]): Unit = {
      import scribe.Level._
      record.level match {
        case Trace if ideaLogger.isTraceEnabled =>
          ideaLogger.trace(record.message)
          ideaLogger.trace(record.throwable.orNull)
        case Debug if ideaLogger.isDebugEnabled =>
          ideaLogger.debug(record.message, record.throwable.orNull)
        case Info =>
          ideaLogger.info(record.message, record.throwable.orNull)
        case Warn =>
          ideaLogger.warn(record.message, record.throwable.orNull)
        case Error =>
          ideaLogger.error(record.message, record.throwable.orNull)
        case _ => // ignore
      }
    }
  }

}
