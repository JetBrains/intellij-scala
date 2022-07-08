package org.jetbrains.plugins.scala.lang.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiFileFactory}
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.{CharSeqExt, IteratorExt, PsiElementExt, StringExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.{MarkersUtils, TestUtils}
import org.junit.Assert._

import java.io.File

/**
 * Base class for java formatter tests that holds utility methods.
 */
// NOTE: initially was almost duplicate from Java
abstract class AbstractScalaFormatterTestBase extends LightIdeaTestCase {

  protected def language: Language = ScalaLanguage.INSTANCE

  protected def getCommonSettings = getSettings.getCommonSettings(language)
  protected def getScalaSettings = getSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
  protected def getIndentOptions = getCommonSettings.getIndentOptions
  protected def getSettings = CodeStyle.getSettings(getProject)

  protected def scalaSettings = getScalaSettings
  protected def commonSettings = getCommonSettings
  protected def ss = getScalaSettings
  protected def cs = getCommonSettings

  implicit protected def project: Project = getProject

  private def codeStyleManager(implicit project: Project): CodeStyleManager =
    CodeStyleManager.getInstance(project)

  override protected def setUp(): Unit = {
    super.setUp()
    TestUtils.disableTimerThread()
  }

  override def tearDown(): Unit = {
    // clean virtual files references to aboid project leaks
    // NOTE: in theory it shouldn't be required because VirtualFiles are not associated with project, they are application-level
    // but for some reason project is leaked in LightVirtualFile via FileManagerImpl.myPsiHardRefKey key, stuck in the user map
    // there was an attempt to fix it in https://github.com/JetBrains/intellij-community/commit/ba9f0e8624ab8e64bd52928e662c154672452ff8
    // but the change was reverted for unknown reason =/
    ScalaFmtPreFormatProcessor.formattedCountMap.clear()

    super.tearDown()
  }

  import scala.jdk.CollectionConverters._

  private val Actions: Map[Action, TestFormatAction] = Map(
    Action.Reformat -> ((file, ranges) => {
      codeStyleManager.reformatText(file, ranges.asJava)
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

  def assertFormatterDoesNotFail(text: String, repeats: Int): Unit =
    doTextTest(TestData.apply(text, None, tempFileName, Action.Reformat, Seq(), repeats, checkAfterEachIteration = false))

  def doTextTest(text: String, textAfter: String, repeats: Int): Unit =
    doTextTest(TestData.reformat(text, textAfter, tempFileName, repeats, checkAfterEachIteration = false))

  def doTextTest(text: String, textAfter: String, repeats: Int, checkAfterEachIteration: Boolean = false): Unit =
    doTextTest(TestData.reformat(text, textAfter, tempFileName, repeats, checkAfterEachIteration))

  def doTextTest(text: String, textAfter: String, fileName: String): Unit =
    doTextTest(TestData.reformat(text, textAfter, fileName))

  def doTextTest(value: String): Unit =
    doTextTest(value, value)

  def doTextTest(value: String, actionRepeats: Int): Unit =
    doTextTest(value, value, actionRepeats)

  private def doTextTest(action: Action, text: String, textAfter: String): Unit =
    doTextTest(TestData(text, textAfter, tempFileName, action, 1, checkAfterEachIteration = false))

  private def initFile(fileName: String, text: String): PsiFile = {
    PsiFileFactory.getInstance(project)
      .createFileFromText(fileName, language, text, true, false)
  }

  /**
   * For a given selection create all possible selections text ranges with borders leaf elements ranges borders.
   * For each selection runs a formatting test and ensures it doesn't break the code.
   * USE WITH CAUTIONS: the amount of selections grows very fast depending on the amount of inner elements.
   * NOTE: for now it only supports selection of a whole valid node
   */
  protected def doAllRangesTextTest(text: String, checkResult: Boolean = true): Unit = {
    val (textClean, selections) = MarkersUtils.extractMarker(text)
    val selection: TextRange = selections match {
      case head :: Nil => head
      case Nil         => TextRange.create(0, textClean.length)
      case other       => fail(s"expecting single range for all ranges test, but got: $other").asInstanceOf[Nothing]
    }

    val file = initFile(tempFileName, textClean)
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

        val expected = prepareText(textClean)
        val documentText = prepareText(document.getText)
        if (checkResult) {
          assertEquals(expected, documentText)

          manager.commitDocument(document)
          val fileText = prepareText(file.getText)
          assertEquals(expected, fileText)
        }

        if (expected != documentText) {
          inWriteAction {
            document.setText(textClean)
          }
          manager.commitDocument(document)
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
    val TestData(textBefore, textAfter, fileName, action, selectedRanges, actionRepeats, checkAfterEachIteration) =
      testData

    assertTrue("action should be applied at least once", actionRepeats >= 1)
    if (actionRepeats > 1 && selectedRanges.nonEmpty)
      fail("for now an action can not be applied multiple times for selection")

    val file = initFile(fileName, textBefore)
    val manager  = PsiDocumentManager.getInstance(getProject)
    val document = manager.getDocument(file)ensuring(_ != null, "Don't expect the document to be null")

    def check(expected: String): Unit = {
      val expected2 = prepareText(expected)
      assertEquals(expected2, prepareText(document.getText))
      manager.commitDocument(document)
      assertEquals(expected2, prepareText(file.getText))
    }

    runCommandInWriteAction(() => try {
      for (_ <- 0 until actionRepeats) {
        val ranges = if (selectedRanges.nonEmpty) selectedRanges else Seq(file.getTextRange)
        Actions(action).run(file, ranges)
        if (checkAfterEachIteration) {
          textAfter.foreach(check)
        }
      }
    } catch {
      case e: IncorrectOperationException =>
        fail(e.getLocalizedMessage)
    }, "", "")


    textAfter.foreach(check)
  }

  protected def prepareText(text: String): String = {
    val textTrimmed = text.trim
    stripTrailingSpaces(textTrimmed)
  }

  private def stripTrailingSpaces(text: String): String = {
    val doc = EditorFactory.getInstance.createDocument(text)
    runCommandInWriteAction(() => {
      doc.asInstanceOf[DocumentImpl].stripTrailingSpaces(getProject)
    }, "formatting: strip trailing spaces", null)
    doc.getText
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
    textAfter: Option[String], // None means that we just want to test that formatter doesnt fail
    fileName: String,
    action: Action,
    ranges: Seq[TextRange],
    actionRepeats: Int,
    checkAfterEachIteration: Boolean
  )

  object TestData {

    def apply(textBefore: String, textAfter: String, fileName: String, action: Action, ranges: Seq[TextRange], actionRepeats: Int): TestData =
      new TestData(textBefore, Some(textAfter), fileName, action, ranges, actionRepeats, checkAfterEachIteration = false)

    def apply(before: String, after: String, fileName: String, action: Action, actionRepeats: Int, checkAfterEachIteration: Boolean): TestData = {
      val (beforeWithoutMarkers, selectedTextRanges) = MarkersUtils.extractNumberedMarkers(before)
      val (afterWithoutMarkers, _) = MarkersUtils.extractNumberedMarkers(after)
      TestData(beforeWithoutMarkers, Some(afterWithoutMarkers), fileName, action, selectedTextRanges, actionRepeats, checkAfterEachIteration)
    }

    def reformat(before: String, after: String, fileName: String, repeats: Int, checkAfterEachIteration: Boolean): TestData =
      TestData(before, after, fileName, Action.Reformat, repeats, checkAfterEachIteration)

    def reformat(before: String, after: String, fileName: String): TestData =
      TestData(before, after, fileName, Action.Reformat, 1, checkAfterEachIteration = false)
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