package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle

abstract class UpgradeWarningsToErrorsCompilerHighlightingTestBase(scalaVersion: ScalaVersion) extends ScalaCompilerHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  def testUpgradeUnusedLocalsWarning(): Unit = {
    setCompilerOptions("-Wunused:locals", "-Xfatal-warnings")

    def highlighting(startOffset: Int, endOffset: Int): ExpectedHighlighting =
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(TextRange.create(startOffset, endOffset)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = "unused local definition"
      )

    runTestCase(
      fileName = "UpgradeUnusedLocalsWarning.scala",
      content =
        """object UpgradeUnusedLocalsWarning:
          |  def main(): Unit =
          |    val abc = 123
          |    val dfe = 456
          |    val ghi = 789
          |""".stripMargin,
      expectedResult = expectedResult(highlighting(64, 67), highlighting(82, 85), highlighting(100, 103))
    )
  }

  def testUpgradeUnusedImportWarning(): Unit = {
    setCompilerOptions("-Wunused:imports", "-Werror")

    def highlighting(startOffset: Int, endOffset: Int): ExpectedHighlighting =
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(TextRange.create(startOffset, endOffset)),
        quickFixDescriptions = List(QuickFixBundle.message("optimize.imports.fix")),
        msgPrefix = ScalaInspectionBundle.message("unused.import.statement")
      )

    runTestCase(
      fileName = "UnusedImportsWithFlag.scala",
      content =
        """import scala.util.control.*
          |import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
          |import scala.collection.mutable.Set
          |
          |class UnusedImportsWithFlag {
          |  val long = new AtomicLong()
          |}""".stripMargin,
      expectedResult = expectedResult(highlighting(0, 27), highlighting(64, 77), highlighting(91, 126))
    )
  }
}

class UpgradeWarningsToErrorsCompilerHighlightingTest_3 extends UpgradeWarningsToErrorsCompilerHighlightingTestBase(ScalaVersion.Latest.Scala_3)

class UpgradeWarningsToErrorsCompilerHighlightingTest_3_Next extends UpgradeWarningsToErrorsCompilerHighlightingTestBase(ScalaVersion.Latest.Scala_3_7)

class UpgradeWarningsToErrorsCompilerHighlightingTest_3_Next_RC extends UpgradeWarningsToErrorsCompilerHighlightingTestBase(ScalaVersion.Latest.Scala_3_Next_RC)
