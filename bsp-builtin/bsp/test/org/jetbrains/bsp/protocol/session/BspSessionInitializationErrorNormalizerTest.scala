package org.jetbrains.bsp.protocol.session

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.{ResponseError, ResponseErrorCode}
import org.jetbrains.bsp.{BspConnectionError, BspTaskCancelled}
import org.junit.Assert.{assertEquals, assertSame}
import org.junit.Test

import java.util.concurrent.{CancellationException, CompletionException, ExecutionException}

/**
 * @see [[BspSessionInitializationErrorNormalizer]]
 */
class BspSessionInitializationErrorNormalizerTest {

  @Test
  def requestCancelledBuildInitializeResponseNormalizesToBspTaskCancelled(): Unit = {
    val error = responseErrorException(ResponseErrorCode.RequestCancelled, "cancelled")

    assertSame(BspTaskCancelled, BspSessionInitializationErrorNormalizer.fromBuildInitializeFailure(error))
  }

  @Test
  def wrappedRequestCancelledBuildInitializeResponseNormalizesToBspTaskCancelled(): Unit = {
    val error = new CompletionException(
      new ExecutionException(responseErrorException(ResponseErrorCode.RequestCancelled, "cancelled"))
    )

    assertSame(BspTaskCancelled, BspSessionInitializationErrorNormalizer.fromBuildInitializeFailure(error))
  }

  @Test
  def buildInitializeJavaCancellationNormalizesToBspTaskCancelled(): Unit = {
    val error = new CompletionException(new CancellationException("cancelled"))

    assertSame(BspTaskCancelled, BspSessionInitializationErrorNormalizer.fromBuildInitializeFailure(error))
  }

  @Test
  def existingBuildInitializeBspErrorIsPreserved(): Unit = {
    val error = BspConnectionError("already classified")

    assertSame(error, BspSessionInitializationErrorNormalizer.fromBuildInitializeFailure(error))
  }

  @Test
  def nonCancelledBuildInitializeResponseNormalizesToBspConnectionError(): Unit = {
    val responseError = responseErrorException(ResponseErrorCode.InternalError, "server failed")

    BspSessionInitializationErrorNormalizer.fromBuildInitializeFailure(responseError) match {
      case error: BspConnectionError =>
        assertEquals("server failed", error.getMessage)
        assertSame(responseError, error.getCause)
      case other =>
        throw new AssertionError(s"Expected BspConnectionError, got $other")
    }
  }

  @Test
  def directAwaitCancellationNormalizesToBspTaskCancelled(): Unit = {
    val error = new CancellationException("cancelled")

    assertSame(BspTaskCancelled, BspSessionInitializationErrorNormalizer.fromSessionInitializationAwaitFailure(error))
  }

  @Test
  def wrappedAwaitBspTaskCancelledNormalizesToBspTaskCancelled(): Unit = {
    val error = new ExecutionException(BspTaskCancelled)

    assertSame(BspTaskCancelled, BspSessionInitializationErrorNormalizer.fromSessionInitializationAwaitFailure(error))
  }

  @Test
  def wrappedAwaitBspConnectionErrorIsPreserved(): Unit = {
    val error = BspConnectionError("already classified")
    val wrapped = new ExecutionException(error)

    assertSame(error, BspSessionInitializationErrorNormalizer.fromSessionInitializationAwaitFailure(wrapped))
  }

  @Test
  def unknownAwaitFailureNormalizesToBspConnectionError(): Unit = {
    val cause = new IllegalStateException("boom")
    val error = new ExecutionException(cause)

    BspSessionInitializationErrorNormalizer.fromSessionInitializationAwaitFailure(error) match {
      case connectionError: BspConnectionError =>
        assertEquals("boom", connectionError.getMessage)
        assertSame(cause, connectionError.getCause)
      case other =>
        throw new AssertionError(s"Expected BspConnectionError, got $other")
    }
  }

  private def responseErrorException(code: ResponseErrorCode, message: String): ResponseErrorException = {
    val error = new ResponseError
    error.setCode(code)
    error.setMessage(message)
    new ResponseErrorException(error)
  }
}
