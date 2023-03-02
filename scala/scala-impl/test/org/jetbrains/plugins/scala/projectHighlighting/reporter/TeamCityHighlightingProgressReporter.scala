package org.jetbrains.plugins.scala.projectHighlighting.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter.TextBasedProgressIndicator
import org.junit.Assert

import java.io.PrintStream
import scala.collection.mutable

class TeamCityHighlightingProgressReporter(
  testClassName: String,
  override val filesWithProblems: Map[String, Set[TextRange]],
) extends HighlightingProgressReporter {

  import TeamCityHighlightingProgressReporter._

  private val tcPrinter = new TeamcityPrinter(System.out)

  override def notifyHighlightingProgress(percent: Int, fileName: String): Unit = {
    tcPrinter.tcPrintProgressMessage(s"$percent% highlighted, started $fileName")
  }

  override def showError(fileError: FileErrorDescriptor): Unit = {
    tcPrinter.tcPrintErrorMessage(s"Found error: ${fileError.summaryString}")
  }

  override def reportFinalResults(): Unit = {
    val errors = unexpectedErrors
    val totalErrors = errors.size

    val testFailureMessageBuilder = new mutable.StringBuilder
    if (totalErrors > 0) {
      testFailureMessageBuilder.append(s"Found $totalErrors unexpected errors:\n")
      errors.map(_.summaryString).foreach { errorSummary =>
        testFailureMessageBuilder.append(errorSummary).append("\n")
      }
    }

    val noErrorsButExpected = unexpectedSuccess
    if (noErrorsButExpected.nonEmpty) {
      val reportSuccess =
        s"""Looks like you've fixed highlighting in files: ${noErrorsButExpected.mkString(", ")}
           |Remove them from `$testClassName.filesWithProblems`
           |""".stripMargin
      if (testFailureMessageBuilder.nonEmpty) {
        testFailureMessageBuilder.append("\n###\n")
      }
      testFailureMessageBuilder.append(reportSuccess)
    }

    val testFailureMessage = testFailureMessageBuilder.toString()
    if (testFailureMessage.nonEmpty) {
      Assert.fail(testFailureMessage)
    }
  }

  override val progressIndicator: ProgressIndicator = new TextBasedProgressIndicator {
    override def setText(text: String): Unit = {
      if (getText != text) {
        super.setText(text)
        tcPrinter.tcPrintProgressMessage(text)
      }
    }

    override def setText2(text: String): Unit = setText(text)
  }

  override def notify(message: String): Unit = tcPrinter.tcPrintProgressMessage(message)
}

object TeamCityHighlightingProgressReporter {

  /**
   * See https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Messages+to+Build+Log
   *
   * @note we also have org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils, we might unify them to some nice API
   */
  //noinspection ScalaUnusedSymbol
  private class TeamcityPrinter(output: PrintStream) {
    //noinspection ApiStatus

    import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils.{escapeTeamcityValue => escape}

    def tcPrint(message: String): Unit = {
      output.println(s"##teamcity[$message]")
    }

    //##teamcity[message text='<message text>' errorDetails='<error details>' status='<status value>']
    def tcPrintMessage(text: String, status: String, errorDetails: Option[String] = None): Unit = {
      val errorDetailsPart = errorDetails.fold("")(d => s"errorDetails='$d'")
      tcPrint(s"message text='${escape(text)}' status='$status' $errorDetailsPart")
    }

    def tcPrintErrorMessage(text: String, errorDetails: Option[String] = None): Unit = {
      tcPrintMessage(text, "ERROR", errorDetails)
    }

    def tcPrintProgressMessage(content: String): Unit = {
      tcPrint(s"progressMessage '${escape(content)}'")
    }

    def  tcPrintBuildProblem(description: String): Unit = {
      tcPrint(s"buildProblem '${escape(description)}'")
    }

    def tcPrintBuildStatus(status: String, text: String): Unit = {
      tcPrint(s"buildStatus status='$status' text='${escape(text)}'")
    }
  }
}
