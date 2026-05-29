package org.jetbrains.sbt.process.options.utils

import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssue
import com.intellij.pom.Navigatable

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*

private[options] final class MessagesCollectingBuildReporter extends NoOpBuildReporter {
  final case class Message(message: String, details: String)

  private val warnings = new ConcurrentLinkedQueue[Message]
  private val infos = new ConcurrentLinkedQueue[Message]
  private val errors = new ConcurrentLinkedQueue[Message]

  def getWarnings: Seq[Message] = snapshot(warnings)

  def getInfos: Seq[Message] = snapshot(infos)

  def getErrors: Seq[Message] = snapshot(errors)

  override def warning(message: String, position: Option[FilePosition]): Unit =
    collect(warnings, message)

  override def warning(message: String, position: Option[FilePosition], details: String): Unit =
    collect(warnings, message, details)

  override def warning(issue: BuildIssue): Unit =
    collect(warnings, issue)

  override def warning(message: String, position: Option[FilePosition], details: String, navigatable: Option[Navigatable]): Unit =
    collect(warnings, message, details)

  override def error(message: String, position: Option[FilePosition]): Unit =
    collect(errors, message)

  override def info(message: String, position: Option[FilePosition]): Unit =
    collect(infos, message)

  override def info(issue: BuildIssue): Unit =
    collect(infos, issue)

  private def collect(queue: ConcurrentLinkedQueue[Message], message: String, details: String = ""): Unit =
    queue.add(Message(textOrEmpty(message), textOrEmpty(details)))

  private def collect(queue: ConcurrentLinkedQueue[Message], issue: BuildIssue): Unit =
    collect(queue, issue.getTitle, issue.getDescription)

  private def snapshot(queue: ConcurrentLinkedQueue[Message]): Seq[Message] =
    queue.asScala.to(ArraySeq)

  private def textOrEmpty(text: String): String =
    Option(text).getOrElse("")
}
