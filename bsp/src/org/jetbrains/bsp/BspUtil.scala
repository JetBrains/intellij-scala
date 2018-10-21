package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException

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
    def toFile: File = Paths.get(uri.getPath).toFile
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

}
