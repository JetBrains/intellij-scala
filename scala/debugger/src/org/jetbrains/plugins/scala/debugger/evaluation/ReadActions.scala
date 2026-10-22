package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator

import java.util.concurrent.{Callable, ExecutionException}

private[debugger] object ReadActions:
  /**
   * Runs `body` in a non-blocking read action on the current thread. A pending write action (e.g. typing in the
   * editor) cancels and restarts `body` instead of waiting for it to finish, so `body` must be free of side effects
   * and should call `ProgressManager.checkCanceled()` regularly, which resolve and type inference do.
   *
   * The debugger manager thread needs the result right away, hence the synchronous execution.
   *
   * @param expireWhen stops the computation for good, e.g. when the debug process is gone
   * @param indicator  if given, cancelling it stops the computation for good as well
   */
  def inNonBlockingReadAction[T](expireWhen: () => Boolean, indicator: Option[ProgressIndicator] = None)(body: => T): T =
    val callable: Callable[T] = () => body
    val action = ReadAction.nonBlocking(callable).expireWhen(() => expireWhen())
    val withIndicator = indicator.fold(action)(action.wrapProgress)
    try withIndicator.executeSynchronously()
    catch
      // checked exceptions thrown by the computation come back wrapped, the debugger expects a bare EvaluateException
      case e: ExecutionException => e.getCause match
        case evaluateException: EvaluateException => throw evaluateException
        case _ => throw e
