package org.jetbrains.plugins.scala.util.reporter

import scala.collection.mutable

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.TextRange

/**
  * @author mutcianm
  * @since 16.05.17.
  */
trait ProgressReporter {
  val errorMessages: mutable.Map[String, Seq[(TextRange, String)]] = mutable.Map.empty

  final def foundErrors(fileName: String) = errorMessages.getOrElse(fileName, Seq.empty)

  final def unexpectedErrors: Seq[(String, TextRange, String)] = errors(expected = false)

  final def expectedErrors: Seq[(String, TextRange, String)] = errors(expected = true)

  private def errors(expected: Boolean): Seq[(String, TextRange, String)] = {
    for {
      (fileName, errors) <- errorMessages.toSeq
      if filesWithProblems.contains(fileName) == expected
      (range, message) <- errors
    } yield {
      (fileName, range, message)
    }
  }

  final def unexpectedSuccess: Set[String] =
    filesWithProblems.filter(fileName => errorMessages.get(fileName).isEmpty)

  private def saveError(fileName: String, range: TextRange, message: String): Unit =
    errorMessages.update(fileName, foundErrors(fileName) :+ (range, message))

  def filesWithProblems: Set[String]

  def showError(fileName: String, range: TextRange, message: String): Unit

  final def reportError(fileName: String, range: TextRange, message: String): Unit = {
    saveError(fileName, range, message)
    if (!filesWithProblems.contains(fileName)) {
      showError(fileName, range, message)
    }
  }

  def notify(message: String)
  def updateHighlightingProgress(percent: Int)
  def reportResults()
  def progressIndicator: ProgressIndicator
}

object ProgressReporter {
  def newInstance(name: String, filesWithProblems: Set[String], reportSuccess: Boolean = true): ProgressReporter = {
    if (sys.env.contains("TEAMCITY_VERSION")) new TeamCityReporter(name, filesWithProblems, reportSuccess)
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
