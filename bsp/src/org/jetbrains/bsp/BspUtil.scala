package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp.Uri
import com.intellij.openapi.diagnostic.Logger
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import scribe.{LogRecord, LoggerSupport}

import scala.meta.jsonrpc.Response

object BspUtil {

  implicit class BspUriOps(bspUri: Uri) {
    def toURI: URI = new URI(bspUri.value)
    def toFile: File = Paths.get(bspUri.toURI.getPath).toFile
  }

  implicit class JsonRpcResponseErrorOps(err: Response.Error) {
    def toBspError: BspError = {
      BspErrorMessage(s"bsp error: ${err.error.message} (${err.error.code})")
    }
  }

  implicit class ResponseErrorExceptionOps(err: ResponseErrorException) {
    def toBspError: BspError = {
      BspErrorMessage(s"bsp error: ${err.getMessage} (${err.getResponseError.getCode})")
    }
  }

  implicit class StringOps(str: String) {
    def toURI: URI = new URI(str)
  }

  implicit class URIOps(uri: URI) {
    def toFile: File = Paths.get(uri.getPath).toFile
  }

  implicit class IdeaLoggerOps(ideaLogger: Logger) {
    def toScribeLogger: IdeaLoggerSupport = new IdeaLoggerSupport(ideaLogger)
  }

  implicit class CompletableFutureOps[T](cf: CompletableFuture[T]) {
    def catchBspErrors :CompletableFuture[Either[BspError,T]] = cf.handle { (result, error) =>
      if (error != null) error match {
        case responseError: ResponseErrorException =>
          Left(responseError.toBspError)
        case other: Throwable => throw other
      } else Right(result)
    }
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
