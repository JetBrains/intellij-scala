package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.reporter.KnownErrors._

import scala.collection.mutable

/**
  * @author mutcianm
  * @since 16.05.17.
  */
trait ProgressReporter {
  val errorMessages: mutable.Map[String, Seq[(TextRange, String)]] = mutable.Map.empty

  final def foundErrors(fileName: String): Seq[(TextRange, String)] = errorMessages.getOrElse(fileName, Seq.empty)

  final def unexpectedErrors: Seq[(String, TextRange, String)] = errors(expected = false)

  final def expectedErrors: Seq[(String, TextRange, String)] = errors(expected = true)

  private def errors(expected: Boolean): Seq[(String, TextRange, String)] = {
    for {
      (fileName, errors) <- errorMessages.toSeq
      (known, unknown) = errors.partition(err => knownErrors(fileName).contains(err._1))
      (range, message) <- if (expected) known else unknown
    } yield {
      (fileName, range, message)
    }
  }

  final def unexpectedSuccess: Set[String] =
    filesWithProblems.keySet.filter(fileName => errorMessages.get(fileName).isEmpty)

  private def saveError(fileName: String, range: TextRange, message: String): Unit =
    errorMessages.update(fileName, foundErrors(fileName) :+ (range, message))

  def filesWithProblems: Map[String, Set[TextRange]]

  private def knownErrors(fileName: String): KnownErrors =
    filesWithProblems.get(fileName).map {
      case set if set.isEmpty => TooManyErrors
      case set                => RangeSet(set)
    }.getOrElse(NoErrors)

  def showError(fileName: String, range: TextRange, message: String): Unit

  final def reportError(fileName: String, range: TextRange, message: String): Unit = {
    saveError(fileName, range, message)
    if (!knownErrors(fileName).contains(range)) {
      showError(fileName, range, message)
    }
  }

  def notify(message: String): Unit
  def updateHighlightingProgress(percent: Int): Unit
  def reportResults(): Unit
  def progressIndicator: ProgressIndicator
}

object ProgressReporter {
  def newInstance(name: String, filesWithProblems: Map[String, Set[TextRange]], reportStatus: Boolean = true): ProgressReporter = {
    if (sys.env.contains("TEAMCITY_VERSION")) new TeamCityReporter(name, filesWithProblems, reportStatus)
    else new ConsoleReporter(filesWithProblems)
  }

  class TextBasedProgressIndicator extends ProgressIndicatorBase(false) {
    private var oldPercent = -1
    override def setText2(text: String): Unit = setText(text)

    override def setText(text: String): Unit = {
      if (getText != text) {
        super.setText(text)
        println(text)
      }
    }

    override def setFraction(fraction: Double): Unit = {
      super.setFraction(fraction)
      val percent = (fraction * 100).toInt
      val rounded = (percent / 10) * 10
      if (rounded != oldPercent) {
        oldPercent = rounded
        setText(s"Downloading project - $rounded%")
      }
    }
  }
}

private sealed trait KnownErrors {
  def contains(range: TextRange): Boolean
}

private object KnownErrors {
  case class RangeSet(errors: Set[TextRange]) extends KnownErrors {
    override def contains(range: TextRange): Boolean = errors.contains(range)
  }

  case object TooManyErrors extends KnownErrors {
    override def contains(range: TextRange): Boolean = true
  }

  case object NoErrors extends KnownErrors {
    override def contains(range: TextRange): Boolean = false
  }
}
