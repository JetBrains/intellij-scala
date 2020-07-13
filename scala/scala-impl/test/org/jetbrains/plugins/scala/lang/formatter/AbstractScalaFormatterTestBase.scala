package org.jetbrains.plugins.scala.lang.formatter

import java.io.File

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, IteratorExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.{MarkersUtils, TestUtils}
import org.junit.Assert._

/**
 * Base class for java formatter tests that holds utility methods.
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:26:29 PM
 */
// NOTE: initially was almost duplicate from Java
abstract class AbstractScalaFormatterTestBase extends LightIdeaTestCase {

  protected def getCommonSettings = getSettings.getCommonSettings(ScalaLanguage.INSTANCE)
  protected def getScalaSettings = getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
  protected def getIndentOptions = getCommonSettings.getIndentOptions
  protected def getSettings = CodeStyle.getSettings(getProject)

  protected def scalaSettings = getScalaSettings
  protected def commonSettings = getCommonSettings
  protected def indentOptions = getCommonSettings
  protected def settings = getCommonSettings

  implicit protected def project: Project = getProject

  private def codeStyleManager(implicit project: Project): CodeStyleManager =
    CodeStyleManager.getInstance(project)

  override protected def setUp(): Unit = {
    super.setUp()
    TestUtils.disableTimerThread()
  }

  import scala.collection.JavaConverters.seqAsJavaList

  private val Actions: Map[Action, TestFormatAction] = Map(
    Action.Reformat -> ((file, ranges) => {
      codeStyleManager.reformatText(file, seqAsJavaList(ranges))
    }),
    Action.Indent -> ((file, ranges) => {
      ranges match {
        case head :: Nil => codeStyleManager.adjustLineIndent(file, head.getStartOffset)
        case _           => throw new UnsupportedOperationException("Adjusting indents for a collection of ranges is not supported in tests.")
      }
    })
  )

  def tempFileName: String =
    getTestName(true) + ".scala"

  def doTest(): Unit =
    doTest(getTestName(false) + ".scala", getTestName(false) + "_after.scala")

  def doTest(fileNameBefore: String, fileNameAfter: String): Unit =
    doTextTest(Action.Reformat, loadFile(fileNameBefore), loadFile(fileNameAfter))

  def doTextTest(text: String, textAfter: String): Unit =
    doTextTest(text, textAfter, 1)

  def doTextTest(text: String, textAfter: String, repeats: Int): Unit =
    doTextTest(TestData.reformat(text, textAfter, tempFileName, repeats))

  def doTextTest(text: String, textAfter: String, fileName: String): Unit =
    doTextTest(TestData.reformat(text, textAfter, fileName))

  def doTextTest(value: String): Unit =
    doTextTest(value, value)

  def doTextTest(value: String, actionRepeats: Int): Unit =
    doTextTest(value, value, actionRepeats)

  private def doTextTest(action: Action, text: String, textAfter: String): Unit =
    doTextTest(TestData(text, textAfter, tempFileName, action, 1))

  /**
   * For a given selection create all possible selections text ranges with borders leaf elements ranges borders.
   * For each selection runs a formatting test and ensures it doesn't break the code.
   * USE WITH CAUTIONS: the amount of selections grows very fast depending on the amount of inner elements.
   * NOTE: for now it only supports selection of a whole valid node
   */
  protected def doAllRangesTextTest(text: String, checkResult: Boolean = true): Unit = {
    val (textClean, selections) = MarkersUtils.extractSequentialMarkers(text.withNormalizedSeparator)
    val selection: TextRange = selections match {
      case head :: Nil => head
      case Nil         => TextRange.create(0, textClean.length)
      case other       => fail(s"expecting single range for all ranges test, but got: $other").asInstanceOf[Nothing]
    }

    val file = createFile(tempFileName, textClean)
    val startElement = file.findElementAt(selection.getStartOffset)
    //val endElement = file.findElementAt(selection.getEndOffset)
    val element = // select non-leaf element
      startElement.withParents.takeWhile(_.startOffset == startElement.startOffset).lastOption.get

    val allRanges = allPossibleSubRanges(element)

    println(s"allRanges.size: ${allRanges.size}")
    if (allRanges.size > 1500)
      fail(s"too many ranges: ${allRanges.size}")

    val manager  = PsiDocumentManager.getInstance(getProject)
    val document = manager.getDocument(file).ensuring(_ != null, "Don't expect the document to be null")
    for { range <- allRanges } {
      try {
        runCommandInWriteAction(() => try {
          Actions(Action.Reformat).run(file, Seq(range))
        } catch {
          case e: IncorrectOperationException =>
            fail(e.getLocalizedMessage)
        }, "", "")
        if (checkResult) {
          val expected = prepareText(textClean)
          assertEquals(expected, prepareText(document.getText))
          manager.commitDocument(document)
          assertEquals(expected, prepareText(file.getText))
        }
      } catch {
        case t: Throwable =>
          System.err.println(s"range: $range")
          System.err.println(s"text: ${textClean.substring(range)}")
          throw t
      }
    }

    val expectedFormats = allRanges.size
    val actualFormats = ScalaFmtPreFormatProcessor.formattedCountMap.get(file.getVirtualFile)
    assertTrue(
      "All generated range should be actually used for formatting",
      actualFormats >= expectedFormats // intermediate formats from platform expected
    )
  }

  private def allPossibleSubRanges(element: PsiElement): Seq[TextRange] = {
    def collectRanges(el: PsiElement): Iterator[TextRange] =
      Iterator(el.getTextRange) ++ el.children.flatMap(collectRanges)

    val allChildRanges = collectRanges(element).toSeq
    val allBorders = allChildRanges.flatMap(r => Seq(r.getStartOffset, r.getEndOffset)).sorted.distinct.toIndexedSeq
    for {
      from <- allBorders.indices.dropRight(1)
      to   <- from + 1 until allBorders.size
    } yield new TextRange(allBorders(from) ,allBorders(to))
  }

  private def doTextTest(testData: TestData): Unit = {
    val TestData(textBefore, textAfter, fileName, action, selectedRanges, actionRepeats) = testData

    assertTrue("action should be applied at least once", actionRepeats >= 1)
    if (actionRepeats > 1 && selectedRanges.nonEmpty)
      fail("for now an action can not be applied multiple times for selection")

    val file = createFile(fileName, textBefore)
    val manager  = PsiDocumentManager.getInstance(getProject)
    val document = manager.getDocument(file)ensuring(_ != null, "Don't expect the document to be null")

    runCommandInWriteAction(() => try {
      for (_ <- 0 until actionRepeats) {
        val ranges = if (selectedRanges.nonEmpty) selectedRanges else Seq(file.getTextRange)
        Actions(action).run(file, ranges)
      }
    } catch {
      case e: IncorrectOperationException =>
        fail(e.getLocalizedMessage)
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

  //noinspection ReferencePassedToNls
  private def runCommandInWriteAction(runnable: Runnable, name: String, groupId: String): Unit =
    CommandProcessor.getInstance.executeCommand(getProject, () => {
      ApplicationManager.getApplication.runWriteAction(runnable)
    }, name, groupId)
}

private object AbstractScalaFormatterTestBase {

  sealed trait Action
  object Action {
    case object Reformat extends Action
    case object Indent extends Action
  }

  case class TestData(
    textBefore: String,
    textAfter: String,
    fileName: String,
    action: Action,
    ranges: Seq[TextRange],
    actionRepeats: Int
  )

  object TestData {
    def apply(before: String, after: String, fileName: String, action: Action, actionRepeats: Int): TestData = {
      val (beforeWithoutMarkers, selectedTextRanges) = MarkersUtils.extractMarkers(before.withNormalizedSeparator)
      val (afterWithoutMarkers, _) = MarkersUtils.extractMarkers(after.withNormalizedSeparator)
      TestData(beforeWithoutMarkers, afterWithoutMarkers, fileName, action, selectedTextRanges, actionRepeats)
    }
    def reformat(before: String, after: String, fileName: String, repeats: Int): TestData = TestData(before, after, fileName, Action.Reformat, repeats)
    def reformat(before: String, after: String, fileName: String): TestData = TestData(before, after, fileName, Action.Reformat, 1)
  }

  private trait TestFormatAction {
    def run(file: PsiFile, ranges: Seq[TextRange]): Unit
  }

  private def loadFile(name: String): String = {
    val fullName = (TestUtils.getTestDataPath + "/psi/formatter") + File.separatorChar + name
    val text = new String(FileUtil.loadFileText(new File(fullName)))
    text.withNormalizedSeparator
  }
}