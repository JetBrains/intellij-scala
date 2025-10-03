package org.jetbrains.plugins.scala.actions.utils

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly

import scala.util.Try

/**
 * The current primary purpose of this listener is to make asynchronous actions testable
 */
@TestOnly
private[actions]
trait ScalaAsyncActionListener {
  /**
   * @param actionClass The class of the action that was completed
   * @param result      Success if the action was completed successfully<br>
   *                    Failure(exception) if the action failed with the given exception (on any thread)
   */
  def actionCompleted(actionClass: Class[_ <: AnAction], result: Try[Unit]): Unit
}

object ScalaAsyncActionListener {
  val Topic = new Topic[ScalaAsyncActionListener]("ScalaAsyncActionListenerTopic", classOf[ScalaAsyncActionListener])
}
