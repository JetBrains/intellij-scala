package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.reporter.KnownErrors._

import scala.collection.mutable

trait ProgressReporter {
  val errorMessages: mutable.Map[String, Seq[ErrorDescriptor]] = mutable.Map.empty

  final def foundErrors(fileName: String): Seq[ErrorDescriptor] = errorMessages.getOrElse(fileName, Seq.empty)

  final def unexpectedErrors: Seq[FileErrorDescriptor] = errors(expected = false)

  final def expectedErrors: Seq[FileErrorDescriptor] = errors(expected = true)

  private def errors(expected: Boolean): Seq[FileErrorDescriptor] = {
    for {
      (fileName, errors) <- errorMessages.toSeq
      (known, unknown) = errors.partition(err => knownErrors(fileName).containsRange(err.range))
      error <- if (expected) known else unknown
    } yield {
      FileErrorDescriptor(fileName, error)
    }
  }

  final def unexpectedSuccess: Set[String] =
    filesWithProblems.keySet.filter(fileName => !errorMessages.contains(fileName))

  private def saveError(fileError: FileErrorDescriptor): Unit = {
    errorMessages.updateWith(fileError.fileName) { oldErrors =>
      val newErrors = oldErrors.toSeq.flatten :+ fileError.error
      Some(newErrors)
    }
  }

  def filesWithProblems: Map[String, Set[TextRange]]

  private def knownErrors(fileName: String): KnownErrors =
    filesWithProblems.get(fileName).map {
      case set if set.isEmpty => TooManyErrors
      case set                => RangeSet(set)
    }.getOrElse(NoErrors)

  def showError(error: FileErrorDescriptor): Unit

  final def reportError(fileError: FileErrorDescriptor): Unit = {
    saveError(fileError)
    if (!knownErrors(fileError.fileName).containsRange(fileError.error.range)) {
      showError(fileError)
    }
  }

  final def reportError(fileName: String, range: TextRange, message: String): Unit = {
    reportError(FileErrorDescriptor(fileName, ErrorDescriptor(range, message)))
  }

  def notify(message: String): Unit
  def updateHighlightingProgress(percent: Int, fileName: String): Unit
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
  def containsRange(range: TextRange): Boolean
}

private object KnownErrors {
  case class RangeSet(errors: Set[TextRange]) extends KnownErrors {
    override def containsRange(range: TextRange): Boolean = errors.contains(range)
  }

  case object TooManyErrors extends KnownErrors {
    override def containsRange(range: TextRange): Boolean = true
  }

  case object NoErrors extends KnownErrors {
    override def containsRange(range: TextRange): Boolean = false
  }
}

final case class ErrorDescriptor(range: TextRange, message: String)
final case class FileErrorDescriptor(fileName: String, error: ErrorDescriptor)