package org.jetbrains.plugins.scala.util

import com.intellij.testFramework.LoggedErrorProcessor

import java.util
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.CollectionHasAsScala

final class CollectingLoggedMessagesProcessor private[util](
  matchesError: CollectingLoggedMessagesProcessor.LoggedError => Boolean,
  matchedErrorActions: util.Set[LoggedErrorProcessor.Action],
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
    val loggedError = LoggedError(category, message, details.toIndexedSeq, Option(t))
    if (matchesError(loggedError)) {
      loggedErrors.add(loggedError)
      matchedErrorActions
    }
    else {
      super.processError(category, message, details, t)
    }
  }
}

object CollectingLoggedMessagesProcessor {
  final case class LoggedError(
    category: String,
    message: String,
    details: Seq[String],
    throwable: Option[Throwable],
  ) {
    /**
     * This can be useful to test that the error contains some text in it but we don't care in the test where exactly
     */
    lazy val allPartsConcatenatedText: String = {
      val parts = Option(message).toSeq ++
        details.flatMap(Option(_)) ++
        Seq(throwable.flatMap(t => Option(t.getMessage)), throwable.map(_.toString))
      parts.mkString("\n")
    }
  }

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
    val processor = collectingWith(errorActions = util.EnumSet.of(LoggedErrorProcessor.Action.LOG))
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

  def collectMatchingErrors[T](matches: LoggedError => Boolean)(body: => T): (T, Seq[LoggedError]) = {
    val processor = new CollectingLoggedMessagesProcessor(
      matchesError = matches,
      matchedErrorActions = LoggedErrorProcessor.Action.NONE,
    )
    val result = executeWithProcessor(processor)(body)
    (result, processor.errors)
  }

  private def collectingWith(errorActions: util.Set[LoggedErrorProcessor.Action]): CollectingLoggedMessagesProcessor =
    new CollectingLoggedMessagesProcessor(_ => true, errorActions)

  private def executeWithProcessor[T](processor: CollectingLoggedMessagesProcessor)(body: => T): T = {
    var result: T = null.asInstanceOf[T]
    LoggedErrorProcessor.executeWith(processor, () => result = body)
    result
  }
}
