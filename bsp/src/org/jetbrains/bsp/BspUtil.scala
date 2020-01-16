package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

import com.intellij.build.events.impl.{FailureResultImpl, SuccessResultImpl}
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.jetbrains.plugins.scala.build.BuildMessages.EventId
import org.jetbrains.plugins.scala.build.BuildTaskReporter

import scala.util.{Failure, Success, Try}

object BspUtil {

  implicit class ResponseErrorExceptionOps(err: ResponseErrorException) {
    def toBspError: BspError = {
      BspErrorMessage(s"bsp error: ${err.getMessage} (${err.getResponseError.getCode})")
    }
  }

  implicit class StringOps(str: String) {
    def toURI: URI = new URI(str)
  }

  implicit class URIOps(uri: URI) {
    def toFile: File = Paths.get(uri).toFile
  }

  implicit class CompletableFutureOps[T](cf: CompletableFuture[T]) {
    def catchBspErrors : CompletableFuture[Try[T]] = cf.handle { (result, error) =>
      if (error != null) error match {
        case responseError: ResponseErrorException =>
          Failure(responseError.toBspError)
        case other: Throwable => throw other
      } else Success(result)
    }

    def reportFinished(reporter: BuildTaskReporter,
                       eventId: EventId,
                       successMsg: String,
                       failMsg: String
                      ): CompletableFuture[T] = {
      cf.thenAccept {
        case Success(_) =>
          reporter.finishTask(eventId, successMsg, new SuccessResultImpl(true))
        case Failure(x)  =>
          reporter.finishTask(eventId, failMsg, new FailureResultImpl(x))
        case _ =>
          reporter.finishTask(eventId, successMsg, new SuccessResultImpl(true))
      }
      cf
    }
  }

}
