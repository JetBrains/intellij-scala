package org.jetbrains.plugins.scala.projectHighlighting.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter.TextBasedProgressIndicator
import org.junit.Assert

import java.io.PrintStream

class TeamCityHighlightingProgressReporter(
  testClassName: String,
  override val filesWithProblems: Map[String, Set[TextRange]],
  reportStatus: Boolean
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

    var testShouldFail = false
    if (totalErrors > 0) {
      testsClassesWithProblems += testClassName
      tcPrinter.tcPrintBuildProblem(s"Found $totalErrors errors in $testClassName")
      tcPrinter.tcPrintErrorMessage(
        s"""Unexpected errors highlighted:
           |${errors.map(_.summaryString).mkString("\n")}""".stripMargin
      )
      testShouldFail = true
    }

    val noErrorsButExpected = unexpectedSuccess
    if (noErrorsButExpected.nonEmpty) {
      testsClassesWithProblems += testClassName

      val reportSuccess =
        s"""Looks like you've fixed highlighting in files: ${noErrorsButExpected.mkString(", ")}
           |Remove them from `$testClassName.filesWithProblems`
           |""".stripMargin
      tcPrinter.tcPrintBuildProblem(reportSuccess)
      testShouldFail = true
    }

    if (reportStatus) {
      //NOTE:
      //build status messages are not collected by TeamCity
      //Each report of build status overrides any build status reported before.
      //Also mind that we collect `testsWithProblems` in static field which is shared by all tests
      //So if we report "Problem found in TestClass1"
      //and later report "Problem found in TestClass1, TestClass2"
      //the resulting status will be "Problem found in TestClass1, TestClass2"
      if (testsClassesWithProblems.isEmpty) {
        // NOTE: do not set build status, otherwise, even if there were failed tests before current test class,
        //  that "FAILURE" status will be overwritten by this 'SUCCESS' status.
        //  It results into "success" builds with failed tests.
      } else {
        val testNames = testsClassesWithProblems.mkString(", ")
        tcPrinter.tcPrintBuildStatus("FAILURE", s"Problems found in $testNames")
      }
    }

    if (testShouldFail) {
      Assert.fail("Unexpected errors highlighted. Please see build problems overview to see the details")
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
  private var testsClassesWithProblems: Set[String] = Set.empty

  /**
   * See https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Messages+to+Build+Log
   *
   * @note we also have org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils, we might unify them to some nice API
   */
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

    def tcPrintBuildProblem(description: String): Unit = {
      tcPrint(s"buildProblem '${escape(description)}'")
    }

    def tcPrintBuildStatus(status: String, text: String): Unit = {
      tcPrint(s"buildStatus status='$status' text='${escape(text)}'")
    }
  }
}
