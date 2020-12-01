package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtTestUtil
import org.hamcrest.{Description, Matcher}
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.withErrorsFromCompiler
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, invokeAndWait}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.matchers.{HamcrestMatchers, ScalaBaseMatcher}
import org.jetbrains.plugins.scala.{ScalaVersion, SlowTests}
import org.junit.Assert.assertThat
import org.junit.experimental.categories.Category

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

@Category(Array(classOf[SlowTests]))
class ScalaCompilerHighlightingTest_2_13 extends ScalaCompilerHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

@Category(Array(classOf[SlowTests]))
class ScalaCompilerHighlightingTest_3_0 extends ScalaCompilerHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_0
}

@Category(Array(classOf[SlowTests]))
abstract class ScalaCompilerHighlightingTestBase
  extends ScalaCompilerTestBase
    with HamcrestMatchers {

  override def useCompileServer: Boolean = true
  override def runInDispatchThread: Boolean = false

  override def setUp(): Unit =
    EdtTestUtil.runInEdtAndWait(() => {
      ScalaCompilerHighlightingTestBase.super.setUp()
    })

  type ExpectedResult = Matcher[Seq[HighlightInfo]]

  def testWarningHighlighting(): Unit = runTestCase(
    fileName = "ExhaustiveMatchWarning.scala",
    content =
      """
        |class ExhaustiveMatchWarning {
        |  val option: Option[Int] = Some(1)
        |  option match {
        |    case Some(_) =>
        |  }
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.WARNING,
      range = Some(new TextRange(70, 76)),
      msgPrefix = "match may not be exhaustive"
    ))
  )

  def testErrorHighlighting(): Unit = runTestCase(
    fileName = "AbstractMethodInClassError.scala",
    content =
      """
        |class AbstractMethodInClassError {
        |  def method: Int
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(new TextRange(7, 33)),
      msgPrefix = "class AbstractMethodInClassError needs to be abstract"
    ))
  )

  protected def runTestCase(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult,
    waitUntilFileIsHighlighted: VirtualFile => Unit
  ): Unit = withErrorsFromCompiler {
    val virtualFile = addFileToProjectSources(fileName, content)
    waitUntilFileIsHighlighted(virtualFile)
    doAssertion(virtualFile, expectedResult)
  }

  protected def doAssertion(virtualFile: VirtualFile,
                            expectedResult: ExpectedResult): Unit = {
    @tailrec
    def rec(attemptsLeft: Int): Unit = {
      val actualResult = invokeAndWait {
        val document = virtualFile.findDocument.get
        DaemonCodeAnalyzerImpl.getHighlights(document, null, getProject).asScala.toSeq
      }
      try {
        assertThat(actualResult, expectedResult)
      } catch {
        case error: AssertionError =>
          if (attemptsLeft > 0) {
            Thread.sleep(2000)
            rec(attemptsLeft - 1)
          } else {
            throw error
          }
      }
    }
    rec(2)
  }

  protected def runTestCase(fileName: String,
                            content: String,
                            expectedResult: ExpectedResult): Unit = withErrorsFromCompiler {
    val waitUntilFileIsHighlighted: VirtualFile => Unit = virtualFile => {
      invokeAndWait {
        FileEditorManager.getInstance(getProject).openFile(virtualFile, true)
        compiler.rebuild()
      }
    }
    runTestCase(fileName, content, expectedResult, waitUntilFileIsHighlighted)
  }

  protected case class ExpectedHighlighting(severity: HighlightSeverity,
                                            range: Option[TextRange] = None,
                                            msgPrefix: String = "")

  protected def expectedResult(expected: ExpectedHighlighting*): ExpectedResult = new ScalaBaseMatcher[Seq[HighlightInfo]] {

    override protected def valueMatches(actualValue: Seq[HighlightInfo]): Boolean = {
      expected.size == actualValue.size &&
        expected.zip(actualValue).forall { case (expected, actual) =>
          actual.getSeverity == expected.severity &&
            expected.range.forall(_ == actual.range) &&
            actual.getDescription.startsWith(expected.msgPrefix)
        }
    }

    override protected def description: String =
      descriptionFor(expected)

    override def describeMismatch(item: Any, description: Description): Unit =
      item match {
        case seq: Seq[HighlightInfo @unchecked] =>
          val itemFixed = descriptionFor(seq.map(toExpectedHighlighting))
          super.describeMismatch(itemFixed, description)
        case _ =>
          super.describeMismatch(item, description)
      }

    private def toExpectedHighlighting(info: HighlightInfo): ExpectedHighlighting =
      ExpectedHighlighting(info.getSeverity, Some(info.range), info.getDescription)

    private def descriptionFor(highlightings: Seq[ExpectedHighlighting]): String =
      highlightings.map(descriptionFor).mkString("\n")

    private def descriptionFor(highlighting: ExpectedHighlighting): String = {
      val ExpectedHighlighting(severity, range, msgPrefix) = highlighting
      val values = Seq(
        "severity" -> severity,
        "range" -> range.getOrElse("?"),
        "msgPrefix" -> msgPrefix
      ).map { case (name, value) =>
        s"$name=$value"
      }.mkString(",")
      s"HighlightInfo($values)"
    }
  }
}
