package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import org.hamcrest.Matcher
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.HighlightInfoExt
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.matchers.{HamcrestMatchers, ScalaBaseMatcher}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertThat
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.collection.JavaConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_0
))
@Category(Array(classOf[HighlightingTests]))
class ScalaCompilerHighlightingTest
  extends ScalaCompilerTestBase
    with HamcrestMatchers {

  import ScalaCompilerHighlightingTest._

  def testErrorHighlighting(): Unit = runTestCase(
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
    expectedResult = expectedResult(
      severity = HighlightSeverity.WARNING,
      range = Some(new TextRange(70, 76)),
      msgPrefix = "match may not be exhaustive"
    )
  )

  def testWarningHighlighting(): Unit = runTestCase(
    fileName = "AbstractMethodInClassError.scala",
    content =
      """
        |class AbstractMethodInClassError {
        |  def method: Int
        |}
        |""".stripMargin,
    expectedResult = expectedResult(
      severity = HighlightSeverity.ERROR,
      range = Some(new TextRange(7, 33)),
      msgPrefix = "class AbstractMethodInClassError needs to be abstract"
    )
  )

  private def runTestCase(fileName: String,
                          content: String,
                          expectedResult: ExpectedResult): Unit = withErrorsFromCompiler {
    val virtualFile = addFileToProjectSources(fileName, content)
    FileEditorManager.getInstance(getProject).openFile(virtualFile, true)

    compiler.rebuild()

    val document = virtualFile.toDocument.get
    val infos = DaemonCodeAnalyzerImpl.getHighlights(document, null, getProject).asScala
    assertThat("Test case supports only one highlighting", infos, hasSize(1))
    val actualResult = infos.head

    assertThat(actualResult, expectedResult)
  }
}

object ScalaCompilerHighlightingTest {

  private type ExpectedResult = Matcher[HighlightInfo]

  private def expectedResult(severity: HighlightSeverity,
                             range: Option[TextRange] = None,
                             msgPrefix: String = ""): ExpectedResult = new ScalaBaseMatcher[HighlightInfo] {
    override protected def valueMatches(actualValue: HighlightInfo): Boolean =
      actualValue.getSeverity == severity &&
        range.forall(_ == actualValue.range) &&
        actualValue.getDescription.startsWith(msgPrefix)

    override protected def description: String = {
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

  private def withErrorsFromCompiler(body: => Unit): Unit = {
    val registry = Registry.get(ScalaHighlightingMode.ShowScalacErrorsKey)

    registry.setValue(true)

    try body
    finally registry.setValue(false)
  }
}
