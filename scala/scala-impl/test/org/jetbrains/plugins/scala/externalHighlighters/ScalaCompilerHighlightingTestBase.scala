package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.testFramework.EdtTestUtil
import org.hamcrest.{Description, Matcher}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, inReadAction, invokeAndWait}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.matchers.{HamcrestMatchers, ScalaBaseMatcher}
import org.junit.Assert.assertThat
import org.junit.experimental.categories.Category

import java.util.Collections.emptyList
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

@Category(Array(classOf[SlowTests]))
abstract class ScalaCompilerHighlightingTestBase
  extends ScalaCompilerTestBase
    with HamcrestMatchers {

  private var myEditor: Editor = _
  private var myPsiFile: PsiFile = _

  override def useCompileServer: Boolean = true
  override def runInDispatchThread: Boolean = false

  override def setUp(): Unit =
    EdtTestUtil.runInEdtAndWait(() => {
      ScalaCompilerHighlightingTestBase.super.setUp()
    })

  override protected def tearDown(): Unit = {
    myEditor = null
    myPsiFile = null
    super.tearDown()
  }

  type ExpectedResult = Matcher[Seq[HighlightInfo]]

  protected def runTestCase(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult,
    waitUntilFileIsHighlighted: VirtualFile => Unit
  ): Unit = runWithErrorsFromCompiler(getProject) {
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
        myEditor = EditorFactory.getInstance().getEditors(document).head
        myPsiFile = PsiDocumentManager.getInstance(getProject).getPsiFile(document)
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
                            expectedResult: ExpectedResult): Unit = runWithErrorsFromCompiler(getProject) {
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
                                            quickFixDescriptions: Seq[String],
                                            msgPrefix: String = "")

  protected def expectedResult(expected: ExpectedHighlighting*): ExpectedResult = new ScalaBaseMatcher[Seq[HighlightInfo]] {

    override protected def valueMatches(actualValue: Seq[HighlightInfo]): Boolean = {
      expected.size == actualValue.size &&
        expected.zip(actualValue).forall { case (expected, actual) =>
          actual.getSeverity == expected.severity &&
            expected.range.forall(_ == actual.range) &&
            actual.getDescription.startsWith(expected.msgPrefix) &&
            quickFixDescriptions(actual).toSet == expected.quickFixDescriptions.toSet
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
      ExpectedHighlighting(info.getSeverity, Some(info.range), quickFixDescriptions(info).toSeq, info.getDescription)

    private def quickFixDescriptions(info: HighlightInfo) = {
      inReadAction {
        Option(info.quickFixActionRanges).getOrElse(emptyList()).asScala
          .map(_.first.getAction)
          .filter(fix => fix.isAvailable(getProject, myEditor, myPsiFile))
          .map(_.getText)
      }
    }

    private def descriptionFor(highlightings: Seq[ExpectedHighlighting]): String =
      highlightings.map(descriptionFor).mkString("\n")

    private def descriptionFor(highlighting: ExpectedHighlighting): String = {
      val ExpectedHighlighting(severity, range, quickFixDescriptions, msgPrefix) = highlighting
      val values = Seq(
        "severity" -> severity,
        "range" -> range.getOrElse("?"),
        "quickFixDescriptions" -> quickFixDescriptions,
        "msgPrefix" -> msgPrefix
      ).map { case (name, value) =>
        s"$name=$value"
      }.mkString(",")
      s"HighlightInfo($values)"
    }
  }
}
