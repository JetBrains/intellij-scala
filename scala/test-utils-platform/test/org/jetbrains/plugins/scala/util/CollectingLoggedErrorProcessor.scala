package org.jetbrains.plugins.scala.util

import com.intellij.testFramework.LoggedErrorProcessor

import java.util
import java.util.EnumSet
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.CollectionHasAsScala

final class CollectingLoggedErrorProcessor extends LoggedErrorProcessor {
  private val loggedErrors: ConcurrentLinkedQueue[Throwable] =
    new ConcurrentLinkedQueue[Throwable]()

  def errors: Seq[Throwable] =
    loggedErrors.asScala.toSeq

  override def processError(
    category: String,
    message: String,
    details: Array[String],
    t: Throwable
  ): util.Set[LoggedErrorProcessor.Action] = {
    if (t != null) {
      loggedErrors.add(t)
    }
    EnumSet.of(LoggedErrorProcessor.Action.LOG)
  }
}

object CollectingLoggedErrorProcessor {
  def collect[T](body: => T): (T, Seq[Throwable]) = {
    val processor = new CollectingLoggedErrorProcessor
    var result: T = null.asInstanceOf[T]
    LoggedErrorProcessor.executeWith(processor, () => result = body)
    (result, processor.errors)
  }
}
