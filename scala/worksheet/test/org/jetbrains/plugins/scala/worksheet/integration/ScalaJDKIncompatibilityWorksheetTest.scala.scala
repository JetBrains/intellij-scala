package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.compiler.CompilerMessageCategory
import org.jetbrains.jps.incremental.scala.utils.ScalaJDKIncompatibilityDetector
import org.jetbrains.plugins.scala.util.runners._
import org.jetbrains.plugins.scala.worksheet.integration.plain.PlainWorksheetTestBase
import org.jetbrains.plugins.scala.worksheet.integration.repl.WorksheetReplIntegrationBaseTest
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test worksheet evaluation to verify that Scala/JDK compatibility warnings appear correctly
 * in both compiler messages and worksheet viewer output (it depends on a worksheet type).
 *
 * @note The test cases (Scala/JDK versions) are the same as in [[ScalaJDKIncompatibilityDetectorTest]].
 *       Keep both test classes synchronized when adding, removing, or updating versions.
 */
@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
trait ScalaJdkIncompatibilityWorksheetTestBase { self: WorksheetIntegrationBaseTest =>
  private val SampleWorksheet: String = "1 + 1"

  protected def validateErrorInViewerText: Boolean = false

  @Test
  def doTest(): Unit = {
    val result = runWorksheetEvaluationAndWait(SampleWorksheet)
    val editor = result.editorAndFile.editor
    val text =
      if (validateErrorInViewerText) {
        val resultEditor = WorksheetCache.getInstance(project).getViewer(editor)
        resultEditor.getDocument.getText()
      } else {
        collectedCompilerMessages(editor)
          .filter(m => m.getCategory == CompilerMessageCategory.ERROR || m.getCategory == CompilerMessageCategory.WARNING)
          .map(_.getMessage)
          .mkString("\n")
      }

    assertTrue(
      s"Expected to contain a JDK incompatibility warning, but got: \n$text",
      text.contains(ScalaJDKIncompatibilityDetector.JdkCompatibilityWarningPrefix)
    )
  }
}

abstract class ScalaJdkCompatibilityWorksheetPlainBase extends PlainWorksheetTestBase with ScalaJdkIncompatibilityWorksheetTestBase {
  override def runInCompileServerProcess: Boolean = false
}

abstract class ScalaJdkCompatibilityWorksheetReplBase extends WorksheetReplIntegrationBaseTest with ScalaJdkIncompatibilityWorksheetTestBase {
  override def validateErrorInViewerText: Boolean = true
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12_0, TestScalaVersion.Scala_2_12_6))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_21))
class WorksheetPlain_Scala2_12 extends ScalaJdkCompatibilityWorksheetPlainBase

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_3_0))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_21))
class WorksheetPlain_Scala3_3_0 extends ScalaJdkCompatibilityWorksheetPlainBase

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_8))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11))
class WorksheetPlain_Scala3_8 extends ScalaJdkCompatibilityWorksheetPlainBase {
  override protected def validateErrorInViewerText = true
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_9))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11))
class WorksheetPlain_Scala3_9 extends ScalaJdkCompatibilityWorksheetPlainBase {
  override protected def validateErrorInViewerText = true
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12_0, TestScalaVersion.Scala_2_12_6))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_21))
class WorksheetRepl_Scala2_12 extends ScalaJdkCompatibilityWorksheetReplBase

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_3_0))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_21))
class WorksheetRepl_Scala3_3_0 extends ScalaJdkCompatibilityWorksheetReplBase

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_8))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11))
class WorksheetRepl_Scala3_8 extends ScalaJdkCompatibilityWorksheetReplBase

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_9))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11))
class WorksheetRepl_Scala3_9 extends ScalaJdkCompatibilityWorksheetReplBase
