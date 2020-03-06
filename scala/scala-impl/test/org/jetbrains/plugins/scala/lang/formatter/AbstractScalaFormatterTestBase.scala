package org.jetbrains.plugins.scala.lang.formatter

import java.io.File

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase.{Action, Actions, loadFile}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

/**
 * Base class for java formatter tests that holds utility methods.
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:26:29 PM
 */
// NOTE: initially was almost duplicate from Java
abstract class AbstractScalaFormatterTestBase extends LightIdeaTestCase {
  var myTextRanges: Seq[TextRange] = Seq()

  protected def getCommonSettings = getSettings.getCommonSettings(ScalaLanguage.INSTANCE)
  protected def getScalaSettings = getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
  protected def getIndentOptions = getCommonSettings.getIndentOptions
  protected def getSettings = CodeStyle.getSettings(getProject)

  protected def scalaSettings = getScalaSettings
  protected def commonSettings = getCommonSettings
  protected def indentOptions = getCommonSettings
  protected def settings = getCommonSettings

  override protected def setUp(): Unit = {
    super.setUp()
    TestUtils.disableTimerThread()
  }

  def doTest(): Unit =
    doTest(getTestName(false) + ".scala", getTestName(false) + "_after.scala")

  def doTest(fileNameBefore: String, fileNameAfter: String): Unit =
    doTextTest(
      Action.Reformat,
      loadFile(fileNameBefore),
      loadFile(fileNameAfter)
    )

  def doTextTest(text: String, textAfter: String): Unit =
    doTextTest(text, textAfter, 1)

  def doTextTest(text: String, textAfter: String, repeats: Int): Unit =
    doTextTest(
      Action.Reformat,
      text.withNormalizedSeparator,
      textAfter.withNormalizedSeparator,
      AbstractScalaFormatterTestBase.TempFileName,
      repeats
    )

  def doTextTest(text: String, textAfter: String, fileName: String): Unit =
    doTextTest(
      Action.Reformat,
      text.withNormalizedSeparator,
      textAfter.withNormalizedSeparator,
      fileName,
      actionRepeats = 1
    )

  def doTextTest(value: String): Unit =
    doTextTest(value, value)

  def doTextTest(value: String, actionRepeats: Int): Unit =
    doTextTest(value, value, actionRepeats)

  private def doTextTest(action: Action, text: String, textAfter: String): Unit =
    doTextTest(action, text, textAfter, AbstractScalaFormatterTestBase.TempFileName, 1)

  private def doTextTest(
    action: Action,
    text: String,
    textAfter: String,
    fileName: String,
    actionRepeats: Int
  ): Unit = {
    assertTrue("action should be applied at least once", actionRepeats >= 1)

    if (actionRepeats > 1 && myTextRanges.nonEmpty)
      fail("for now an action can not be applied multiple times for selection")

    val file = createFile(fileName, text)
    val manager  = PsiDocumentManager.getInstance(getProject)
    val document = manager.getDocument(file)
    if (document == null)
      fail("Don't expect the document to be null")

    runCommandInWriteAction(() => {
      document.replaceString(0, document.getTextLength, text)
      manager.commitDocument(document)
      for (_ <- 0 until actionRepeats) {
        try {
          if (myTextRanges.size > 1) {
            Actions(action).run(file, myTextRanges)
          } else {
            val rangeToUse = myTextRanges.headOption.getOrElse(file.getTextRange)
            Actions(action).run(file, rangeToUse.getStartOffset, rangeToUse.getEndOffset)
          }
        } catch {
          case e: IncorrectOperationException =>
            fail(e.getLocalizedMessage)
        }
      }
    }, "", "")
    assertEquals(prepareText(textAfter), prepareText(document.getText))
    manager.commitDocument(document)
    assertEquals(prepareText(textAfter), prepareText(file.getText))
  }

  private def prepareText(actual0: String) = {
    val actual1 = if (actual0.startsWith("\n")) actual0.substring(1) else actual0
    val actual2 =  if (actual1.startsWith("\n")) actual1.substring(1) else actual1
    // Strip trailing spaces
    val doc = EditorFactory.getInstance.createDocument(actual2)
    runCommandInWriteAction(() => {
      doc.asInstanceOf[DocumentImpl].stripTrailingSpaces(getProject)
    }, "formatting", null)
    doc.getText.trim
  }

  private def runCommandInWriteAction(runnable: Runnable, name: String, groupId: String): Unit =
    CommandProcessor.getInstance.executeCommand(getProject, () => {
      ApplicationManager.getApplication.runWriteAction(runnable)
    }, name, groupId)
}

object AbstractScalaFormatterTestBase {
  private val TempFileName = "A.scala"

  private sealed trait Action
  private object Action {
    case object Reformat extends Action
    case object Indent extends Action
  }

  private trait TestFormatAction {
    def run(psiFile: PsiFile, startOffset: Int, endOffset: Int): Unit
    def run(psiFile: PsiFile, formatRanges: Seq[TextRange]): Unit
  }

  import scala.collection.JavaConverters.seqAsJavaList

  private val Actions: Map[Action, TestFormatAction] = Map(
    Action.Reformat -> new AbstractScalaFormatterTestBase.TestFormatAction() {
      override def run(psiFile: PsiFile, startOffset: Int, endOffset: Int): Unit =
        CodeStyleManager.getInstance(psiFile.getProject).reformatText(psiFile, startOffset, endOffset)

      override def run(psiFile: PsiFile, formatRanges: Seq[TextRange]): Unit =
        CodeStyleManager.getInstance(psiFile.getProject).reformatText(psiFile, seqAsJavaList(formatRanges))
    },
    Action.Indent -> new AbstractScalaFormatterTestBase.TestFormatAction() {
      override def run(psiFile: PsiFile, startOffset: Int, endOffset: Int): Unit =
        CodeStyleManager.getInstance(psiFile.getProject).adjustLineIndent(psiFile, startOffset)

      override def run(psiFile: PsiFile, formatRanges: Seq[TextRange]): Unit =
        throw new UnsupportedOperationException("Adjusting indents for a collection of ranges is not supported in tests.")
    }
  )

  private def loadFile(name: String) = {
    val fullName = (TestUtils.getTestDataPath + "/psi/formatter") + File.separatorChar + name
    val text = new String(FileUtil.loadFileText(new File(fullName)))
    text.withNormalizedSeparator
  }
}