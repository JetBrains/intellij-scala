package org.jetbrains.bsp.protocol.session

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.jetbrains.bsp.{BspConnectionError, BspError, BspTaskCancelled}

/**
 * Normalizes raw failures from BSP session initialization into the [[BspError]] values used by [[BspSession]].
 *
 * The initialization path observes errors at two Java future boundaries:
 *  1. the `buildInitialize` callback
 *  2. the later blocking wait for the initialized session.
 *
 * Java future wrappers and BSP protocol `RequestCancelled` responses should not turn a user-initiated project close
 * into a visible sync failure, so cancellation-shaped failures become [[BspTaskCancelled]].
 *
 * Non-cancellation failures are:
 *  1. preserved when already classified as [[BspError]]
 *  2. or wrapped as [[BspConnectionError]] when they come from lower-level Java or protocol APIs
 */
private[session] object BspSessionInitializationErrorNormalizer {

  /**
   * Converts failures observed by the `buildInitialize` `CompletableFuture` stage into session-domain failures.
   *
   * Java future cancellation and the BSP protocol-level cancelled-initialize response both become [[BspTaskCancelled]].<br>
   * Other non-domain failures become [[BspConnectionError]] before the initialization result is later observed through `Future.get`.
   */
  def fromBuildInitializeFailure(error: Throwable): BspError =
    BspJavaFutureFailure.unwrap(error) match {
      case BspTaskCancelled =>
        BspTaskCancelled
      case responseError: ResponseErrorException if isRequestCancelled(responseError) =>
        BspTaskCancelled
      case unwrapped if BspJavaFutureFailure.isCancellation(unwrapped) =>
        BspTaskCancelled
      case bspError: BspError =>
        bspError
      case unwrapped =>
        connectionError(unwrapped)
    }

  /**
   * Converts failures observed while waiting for the initialized session future into session-domain failures.
   *
   * At this point BSP protocol failures from `buildInitialize` should already be classified by [[fromBuildInitializeFailure]],
   * s o this method only handles Java future cancellation/wrapping and preserves already-classified [[BspError]] values.
   */
  def fromSessionInitializationAwaitFailure(error: Throwable): BspError =
    BspJavaFutureFailure.unwrap(error) match {
      case BspTaskCancelled =>
        BspTaskCancelled
      case unwrapped if BspJavaFutureFailure.isCancellation(unwrapped) =>
        BspTaskCancelled
      case bspError: BspError =>
        bspError
      case unwrapped =>
        connectionError(unwrapped)
    }

  //noinspection ReferencePassedToNls
  private def connectionError(error: Throwable): BspConnectionError =
    BspConnectionError(error.getMessage, error)

  private def isRequestCancelled(error: ResponseErrorException): Boolean = {
    val responseError = Option(error.getResponseError)
    responseError.exists(_.getCode == ResponseErrorCode.RequestCancelled.getValue)
  }
}
