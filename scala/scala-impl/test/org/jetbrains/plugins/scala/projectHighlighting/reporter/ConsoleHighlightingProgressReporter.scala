package org.jetbrains.plugins.scala.projectHighlighting.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter.TextBasedProgressIndicator
import org.jetbrains.plugins.scala.projectHighlighting.reporter.IndentUtils.StringExt
import org.junit.Assert

import scala.collection.mutable

class ConsoleHighlightingProgressReporter(
  testClassName: String,
  override val filesWithProblems: Map[String, Set[TextRange]]
) extends HighlightingProgressReporter {

  private val errors = mutable.ArrayBuffer.empty[FileErrorDescriptor]

  private var numberOfErrorsNotifiedForCurrentFile = 0
  private val MaxNumberOfErrorsToNotifyPerFile = 5

  override def showError(fileError: FileErrorDescriptor): Unit = {
    val errorSummary = fileError.summaryString
    if (numberOfErrorsNotifiedForCurrentFile < MaxNumberOfErrorsToNotifyPerFile) {
      System.err.println(s"Error: $errorSummary")
    }
    else if (numberOfErrorsNotifiedForCurrentFile == MaxNumberOfErrorsToNotifyPerFile) {
      System.err.println(s"...more errors hidden for current file while highlighting is still in progress")
    }
    numberOfErrorsNotifiedForCurrentFile += 1
    errors += fileError
  }

  override def notifyHighlightingProgress(percent: Int, fileName: String): Unit = {
    println(s"$percent% highlighted, started $fileName")
    numberOfErrorsNotifiedForCurrentFile = 0
  }

  override def reportFinalResults(): Unit = {
    val reportText = new mutable.StringBuilder
    if (errors.nonEmpty) {
      reportText.append("Unexpected highlighting errors found:\n")
      reportText.append(errors.map(_.summaryString).mkString("\n"))
      reportText.append("\n\n")
    }

    val noErrorsButExpected = unexpectedSuccess
    if (noErrorsButExpected.nonEmpty) {
      val reportSuccess =
        s"""Looks like you've fixed highlighting in files:
           |${noErrorsButExpected.mkString(s"\n").indented(2)}
           |Remove them from `$testClassName.filesWithProblems`
           |
           |""".stripMargin
      reportText.append(reportSuccess)
      reportText.append("\n")
    }

    val allErrors = expectedErrors ++ unexpectedErrors
    if (allErrors.nonEmpty) {
      val groupedByFileErrorsText = buildGroupedByFileErrorsText(allErrors)
      reportText.append(
        s"""Highlighted errors grouped by file (${allErrors.size} errors in ${allErrors.map(_.fileName).distinct.size} files):
           |import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange
           |override protected def filesWithProblems: Map[String, Set[TextRange]] = $groupedByFileErrorsText""".stripMargin
      )
    }

    val expected = expectedErrors.map(_.summaryString)
    if (expected.nonEmpty) {
      println(
        s"""### Highlighted expected errors:
           |${expected.mkString("\n")}
           |""".stripMargin
      )
    }

    if (unexpectedErrors.nonEmpty || noErrorsButExpected.nonEmpty) {
      Assert.fail(reportText.toString())
    }
  }

  private def buildGroupedByFileErrorsText(fileErrors: Seq[FileErrorDescriptor]): String = {
    val maxErrorsPerTip = 20

    def entryText(fileName: String, fileErrors: Seq[ErrorDescriptor]): String = {
      val errorsPresentation: String = {
        val fileErrorsFirst = fileErrors.take(maxErrorsPerTip)
        val extraSyntheticMessageLine = if (fileErrors.length > maxErrorsPerTip) Some(s"... (${fileErrors.length - maxErrorsPerTip} more)") else None
        val fileErrorsTexts = fileErrorsFirst.map { err => s"${err.range}, // ${err.message}" } ++ extraSyntheticMessageLine
        s"""Set(
           |${fileErrorsTexts.mkString("\n").indented(2)}
           |)""".stripMargin
      }
      s""""$fileName" -> $errorsPresentation"""
    }

    val errorsPresentations: Seq[String] = {
      val entries = fileErrors.groupBy(_.fileName)
        .view.mapValues(_.sortBy(_.error.range.getStartOffset)
        .map(_.error)).toSeq
        .sortBy(_._1)
      for {
        (fileName, fileErrors) <- entries
      } yield {
        entryText(fileName, fileErrors)
      }
    }

    s"""Map(
       |${errorsPresentations.mkString(s",\n").indented(2)}
       |)""".stripMargin
  }


  override val progressIndicator: ProgressIndicator = new TextBasedProgressIndicator

  override def notify(message: String): Unit = {
    println(message)
  }
}