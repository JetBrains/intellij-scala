package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.testFramework.EdtTestUtil
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.{Description, Matcher}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, inReadAction, invokeAndWait}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.util.matchers.{HamcrestMatchers, ScalaBaseMatcher}
import org.junit.experimental.categories.Category

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
            Thread.sleep(3000)
            rec(attemptsLeft - 1)
          } else {
            throw error
          }
      }
    }
    rec(40)
  }

  protected def runTestCase(fileName: String,
                            content: String,
                            expectedResult: ExpectedResult): Unit = runWithErrorsFromCompiler(getProject) {
    val waitUntilFileIsHighlighted: VirtualFile => Unit = virtualFile => {
      invokeAndWait {
        val descriptor = new OpenFileDescriptor(getProject, virtualFile)
        val editor = FileEditorManager.getInstance(getProject).openTextEditor(descriptor, true)
        // The tests are running in a headless environment where focus events are not propagated.
        // We need to call our listener manually.
        new CompilerHighlightingEditorFocusListener(editor).focusGained()
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
      ExpectedHighlighting(info.getSeverity, Some(info.range), quickFixDescriptions(info), info.getDescription)

    private def quickFixDescriptions(info: HighlightInfo) = {
      inReadAction {
        val builder = Seq.newBuilder[String]
        info.findRegisteredQuickFix { (descriptor, _) =>
          val action = descriptor.getAction
          if (action.isAvailable(getProject, myEditor, myPsiFile)) {
            builder += action.getText
          }
          null
        }
        builder.result()
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

  protected def setCompilerOptions(options: String*): Unit = {
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      additionalCompilerOptions = options
    )
    defaultProfile.setSettings(newSettings)
  }

  protected def withUseCompilerRangesDisabled(test: => Unit): Unit = {
    try {
      ScalaProjectSettings.in(getProject).setUseCompilerRanges(false)
      test
    } finally {
      ScalaProjectSettings.in(getProject).setUseCompilerRanges(true)
    }
  }
}
