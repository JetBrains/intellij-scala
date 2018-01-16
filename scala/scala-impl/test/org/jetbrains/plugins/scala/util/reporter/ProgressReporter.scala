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

  final def foundErrors(fileName: String): Seq[(TextRange, String)] = errorMessages.getOrElse(fileName, Seq.empty)

  final def unexpectedErrors: Seq[(String, TextRange, String)] = errors(expected = false)

  final def expectedErrors: Seq[(String, TextRange, String)] = errors(expected = true)

  private def errors(expected: Boolean): Seq[(String, TextRange, String)] = {
    for {
      (fileName, errors) <- errorMessages.toSeq
      if (filesWithProblems.get(fileName) match {
        case Some(empty) if empty.isEmpty => true
        case other => other.contains(errors.map(_._1).toSet)
      }) == expected
      (range, message) <- errors
    } yield {
      (fileName, range, message)
    }
  }

  final def unexpectedSuccess: Set[String] =
    filesWithProblems.keySet.filter(fileName => errorMessages.get(fileName).isEmpty)

  private def saveError(fileName: String, range: TextRange, message: String): Unit =
    errorMessages.update(fileName, foundErrors(fileName) :+ (range, message))

  def filesWithProblems: Map[String, Set[TextRange]]

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
  def newInstance(name: String, filesWithProblems: Map[String, Seq[(Int, Int)]], reportSuccess: Boolean = true): ProgressReporter = {
    if (sys.env.contains("TEAMCITY_VERSION")) new TeamCityReporter(name, textRange(filesWithProblems), reportSuccess)
    else new ConsoleReporter(textRange(filesWithProblems))
  }

  private def textRange(map: Map[String, Seq[(Int, Int)]]): Map[String, Set[TextRange]] =
    map.map { case (name, ranges) => (name, ranges.map { p => new TextRange(p._1, p._2) }.toSet) }

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
