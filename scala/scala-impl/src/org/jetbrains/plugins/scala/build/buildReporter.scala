package org.jetbrains.plugins.scala.build

import java.util.function.BiPredicate

import com.intellij.build._
import com.intellij.build.events._
import com.intellij.task._

trait BuildReporter {
  def start()

  def finish(messages: BuildMessages): Unit
  def finishWithFailure(err: Throwable): Unit
  def finishCanceled(): Unit

  def warning(message: String, position: Option[FilePosition]): Unit
  def error(message: String, position: Option[FilePosition]): Unit
  def info(message: String, position: Option[FilePosition]): Unit

  def log(message: String): Unit
}

case class TaskManagerResult(context: ProjectTaskContext,
                             isAborted: Boolean,
                             hasErrors: Boolean) extends ProjectTaskManager.Result {

  override def getContext: ProjectTaskContext = context

  override def anyTaskMatches(predicate: BiPredicate[_ >: ProjectTask, _ >: ProjectTaskState]): Boolean =
    false // TODO figure out what this is supposed to do?
}

case class BuildFailure(message: String) extends events.impl.FailureImpl(message, /*description*/ null: String)

case class BuildWarning(message: String) extends Warning {
  override def getMessage: String = message
  override def getDescription: String = null
}
