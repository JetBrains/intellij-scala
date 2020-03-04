package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.hamcrest.Matcher
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.HighlightInfoExt
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.SoftAssert
import org.jetbrains.plugins.scala.util.matchers.{HamcrestMatchers, ScalaBaseMatcher}
import org.junit.Assert
import org.junit.experimental.categories.Category

import scala.collection.JavaConverters._

@Category(Array(classOf[HighlightingTests]))
class ScalaCompilerHighlightingTest
  extends ScalaCompilerTestBase
    with HamcrestMatchers {

  import ScalaCompilerHighlightingTest._

  override protected def setUp(): Unit = {
    super.setUp()
    Registry.get(ScalaHighlightingMode.ShowScalacErrorsKey).setValue(true, getTestRootDisposable)
  }

  private val testsCases = Seq(
    TestCase(
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
    ),
    TestCase(
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
  )

  def testRunTestCases(): Unit = new SoftAssert {
    val virtualFileToExpectedResult = testsCases.map { case TestCase(fileName, content, expectedResult) =>
      val virtualFile = addFileAndOpenInEditor(fileName, content)
      virtualFile -> expectedResult
    }
    compiler.rebuild()
    virtualFileToExpectedResult.map { case (virtualFile, expectedResult) =>
      val actualResult = getActualResult(virtualFile)
      assertThat(virtualFile.getName, actualResult, expectedResult)
    }
  }.assertAll()

  private def addFileAndOpenInEditor(fileName: String, content: String): VirtualFile = {
    val virtualFile = addFileToProjectSources(fileName, content)
    FileEditorManager.getInstance(getProject).openFile(virtualFile, true)
    virtualFile
  }

  private def getActualResult(virtualFile: VirtualFile): HighlightInfo = {
    val document = virtualFile.toDocument.get
    val infos = DaemonCodeAnalyzerImpl.getHighlights(document, null, getProject).asScala
    val assertMsg = s"Test case supports only one highlighting (${virtualFile.getName})"
    Assert.assertThat(assertMsg, infos, hasSize(1))
    infos.head
  }
}

object ScalaCompilerHighlightingTest {

  private case class TestCase(fileName: String,
                              content: String,
                              expectedResult: ExpectedResult)

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
}
