package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.plugins.scala.actions.utils.ScalaAsyncActionListener
import org.jetbrains.plugins.scala.extensions.invokeLater

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Promise, TimeoutException}
import scala.util.Try

object ScalaAsyncActionTestUtils {

  /**
   * Note, this method can wait for action completion only if the action invokes code that notifies instances of
   * [[org.jetbrains.plugins.scala.actions.utils.ScalaAsyncActionListener]]
   *
   * @param invokedActionClass   class of the action that is expected to be invoked needed to filter out irrelevant action
   * @param actionInvocationBody code that is responsible for invoking the action, invoked on EDT asynchronously
   * @param waitForDuration      how long to wait for the action to complete
   */
  @RequiresBackgroundThread // We are waiting for an action result, so we shouldn't block the EDT
  def invokeActionAndWaitForCompletion(
    project: Project,
    invokedActionClass: Class[_],
    actionInvocationBody: () => Unit,
    waitForDuration: Duration = 20.seconds
  ): Unit = {
    val messageBusConnection = project.getMessageBus.connect()
    try {
      val actionResultPromise: Promise[Unit] = Promise()
      val listener: ScalaAsyncActionListener = (actionClass, result) => {
        if (actionClass == invokedActionClass) {
          actionResultPromise.complete(result)
        }
      }
      messageBusConnection.subscribe[ScalaAsyncActionListener](ScalaAsyncActionListener.Topic, listener)

      invokeLater {
        actionInvocationBody()
      }

      // Ignoring the result as it's Unit anyway.
      // All exceptions during the action execution will be thrown at this moment if the promise was completed with failure.
      try Await.ready(actionResultPromise.future, waitForDuration) catch {
        case timeout: TimeoutException =>
          throw new AssertionError(s"The action was not completed in the time span of $waitForDuration", timeout)
      }
      val result: Try[Unit] = actionResultPromise.future.value.get
      result.failed.foreach(ex => {
        throw new AssertionError(s"An exception was thrown during action invocation", ex)
      })
    } finally {
      messageBusConnection.disconnect()
    }
  }
}
