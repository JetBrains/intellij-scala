package org.jetbrains.plugins.scala.projectHighlighting.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.reporter.ConsoleHighlightingProgressReporter.StringExt
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter.TextBasedProgressIndicator
import org.junit.Assert

import scala.collection.mutable

class ConsoleHighlightingProgressReporter(override val filesWithProblems: Map[String, Set[TextRange]]) extends HighlightingProgressReporter {
  private val report = new mutable.StringBuilder("\n")

  private def formatMessage(fileError: FileErrorDescriptor): String = {
    val error = fileError.error
    s"Error: ${fileError.fileName}${error.range.toString} - ${error.message}\n"
  }

  override def showError(fileError: FileErrorDescriptor): Unit =
    report.append(formatMessage(fileError))

  override def updateHighlightingProgress(percent: Int, fileName: String): Unit = {
    println(s"$percent% highlighted, started $fileName")
  }

  override def reportResults(): Unit = {
    val errorsTip = expectedErrorsTip(expectedErrors ++ unexpectedErrors)

    val noErrorsButExpected = unexpectedSuccess
    if (noErrorsButExpected.nonEmpty) {
      val reportSuccess =
        s"""Looks like you've fixed highlighting in files:
           |${noErrorsButExpected.mkString(s"\n").indented(2)}
           |Remove them from `filesWithProblems` of a test case.
           |
           |""".stripMargin
      report.append(reportSuccess)
    }

    report.append(s"Errors tip:\n$errorsTip")

    Assert.assertTrue(report.toString(), unexpectedErrors.isEmpty && noErrorsButExpected.isEmpty)

    val expected = expectedErrors.map(formatMessage)
    if (expected.nonEmpty) {
      println(expected.mkString("\nHighlighting errors in problematic files: \n", "\n", ""))
    }
  }

  private def expectedErrorsTip(fileErrors: Seq[FileErrorDescriptor]): String = {
    val maxErrorsPerTip = 7

    def entryText(fileName: String, fileErrors: Seq[ErrorDescriptor]): String = {
      val errorsPresentation: String =
        if (fileErrors.length > maxErrorsPerTip)
          "<too many errors>" // TODO: print at least some
        else {
          val fileErrorsTexts = fileErrors.map { err => s"${err.range} - ${err.message}" }
          s"""(
             |${fileErrorsTexts.mkString("\n").indented(2)}
             |)""".stripMargin
        }
      s""""$fileName" -> $errorsPresentation"""
    }

    val errorsPresentations = {
      val entries = fileErrors.groupBy(_.fileName).view.mapValues(_.map(_.error)).toSeq.sortBy(_._1)
      for {
        (fileName, fileErrors) <- entries
      } yield {
        entryText(fileName, fileErrors)
      }
    }

    s"""Map(
      |${errorsPresentations.mkString(s"\n").indented(2)}
      |)""".stripMargin
  }


  override val progressIndicator: ProgressIndicator = new TextBasedProgressIndicator
  override def notify(message: String): Unit = println(message)
}

object ConsoleHighlightingProgressReporter {
  implicit class StringExt(private val str: String) extends AnyVal {
    def indented(spaces: Int): String = {
      val indentStr = " " * spaces
      indentStr + str.replace("\n", "\n" + indentStr)
    }
  }
}
