package org.jetbrains.plugins.scala.util

import com.intellij.testFramework.LoggedErrorProcessor

import java.util
import java.util.EnumSet
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.CollectionHasAsScala

final class CollectingLoggedMessagesProcessor private(
  errorActions: util.Set[LoggedErrorProcessor.Action],
) extends LoggedErrorProcessor {
  import CollectingLoggedMessagesProcessor._

  private val loggedErrors: ConcurrentLinkedQueue[LoggedError] =
    new ConcurrentLinkedQueue[LoggedError]()

  private val loggedWarnings: ConcurrentLinkedQueue[LoggedWarning] =
    new ConcurrentLinkedQueue[LoggedWarning]()

  def errors: Seq[LoggedError] =
    loggedErrors.asScala.toSeq

  def warnings: Seq[LoggedWarning] =
    loggedWarnings.asScala.toSeq

  override def processWarn(
    category: String,
    message: String,
    t: Throwable
  ): Boolean = {
    loggedWarnings.add(LoggedWarning(category, message, Option(t)))
    super.processWarn(category, message, t)
  }

  override def processError(
    category: String,
    message: String,
    details: Array[String],
    t: Throwable
  ): util.Set[LoggedErrorProcessor.Action] = {
    loggedErrors.add(LoggedError(category, message, details.toIndexedSeq, Option(t)))
    errorActions
  }
}

object CollectingLoggedMessagesProcessor {
  final case class LoggedError(
    category: String,
    message: String,
    details: Seq[String],
    throwable: Option[Throwable],
  )

  final case class LoggedWarning(
    category: String,
    message: String,
    throwable: Option[Throwable],
  )

  final case class CollectedMessages(
    errors: Seq[LoggedError],
    warnings: Seq[LoggedWarning],
  )

  def collectErrors[T](body: => T): (T, Seq[LoggedError]) = {
    val processor = collectingWith(errorActions = EnumSet.of(LoggedErrorProcessor.Action.LOG))
    val result = executeWithProcessor(processor)(body)
    (result, processor.errors)
  }

  def collectErrorsAndWarnings[T](body: => T): (T, CollectedMessages) = {
    val processor = collectingWith(errorActions = LoggedErrorProcessor.Action.ALL)
    val result = executeWithProcessor(processor)(body)
    (result, CollectedMessages(processor.errors, processor.warnings))
  }

  def collectErrorThrowables[T](body: => T): (T, Seq[Throwable]) = {
    val (result, errors) = collectErrors(body)
    (result, errors.flatMap(_.throwable))
  }

  private def collectingWith(errorActions: util.Set[LoggedErrorProcessor.Action]): CollectingLoggedMessagesProcessor =
    new CollectingLoggedMessagesProcessor(errorActions)

  private def executeWithProcessor[T](processor: CollectingLoggedMessagesProcessor)(body: => T): T = {
    var result: T = null.asInstanceOf[T]
    LoggedErrorProcessor.executeWith(processor, () => result = body)
    result
  }
}
