package org.jetbrains.plugins.hocon

import java.io.File

import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.LightPlatformTestCase.getProject
import com.intellij.testFramework.{EditorTestUtil, LightPlatformTestCase}
import com.intellij.util.LocalTimeCounter
import junit.framework.TestSuite
import org.jetbrains.plugins.hocon.lang.HoconLanguage
import org.jetbrains.plugins.hocon.psi.HoconPsiFile
import org.junit.Assert.{assertEquals, assertTrue}

/**
  * @author ghik
  */
abstract class HoconFileSetTestCase(subpath: String) extends TestSuite {

  //  extends FileSetTestCase(TestUtils.getTestDataPath + "/hocon/" + subpath) {

  protected def transform(data: Seq[String]): String

  protected def preprocessData(parts: Seq[String]): Seq[String] =
    parts

  import HoconFileSetTestCase._

  protected def runTest(file: File): Unit = {
    val fileContents = new String(FileUtil.loadFileText(file, "UTF-8")).replaceAllLiterally("\r", "")
    val allParts = preprocessData(fileContents.split("-{5,}").map(trimNewLines).toSeq)
    assertTrue(allParts.nonEmpty)

    val data = allParts.init
    val expectedResult = allParts.last
    assertEquals(expectedResult, trimNewLines(transform(data).replaceAllLiterally("\r", "")))
  }

  protected def setUp(): Unit = {
    val settings = CodeStyleSettingsManager.getInstance(LightPlatformTestCase.getProject)
      .getCurrentSettings
      .getCommonSettings(HoconLanguage)

    val indentOptions = settings.getIndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
  }
}

object HoconFileSetTestCase {

  private[hocon] def createPseudoPhysicalHoconFile(text: String): HoconPsiFile = {
    val project = LightPlatformTestCase.getProject

    val tempFile = project.getBaseDir + "temp.conf"
    val fileType = FileTypeManager.getInstance.getFileTypeByFileName(tempFile)
    PsiFileFactory.getInstance(project)
      .createFileFromText(tempFile, fileType, text, LocalTimeCounter.currentTime(), true)
      .asInstanceOf[HoconPsiFile]
  }

  private[hocon] def extractCaret(fileText: String): (String, Int) = {
    import EditorTestUtil.CARET_TAG
    val caretOffset = fileText.indexOf(CARET_TAG)

    val newFileText =
      if (caretOffset >= 0) fileText.substring(0, caretOffset) + fileText.substring(caretOffset + CARET_TAG.length)
      else fileText

    (newFileText, caretOffset)
  }

  private[hocon] def inWriteCommandAction[T](body: => T): T = {
    val computable = new Computable[T] {
      override def compute(): T = body
    }

    new WriteCommandAction[T](getProject, "Undefined") {
      protected def run(result: Result[T]): Unit = {
        result.setResult(computable.compute())
      }
    }.execute.getResultObject
  }


  private def trimNewLines(str: String) = {
    val preTrimmed = str.substring(str.prefixLength(_ == '\n'))
    val endingNewlines = preTrimmed.reverseIterator.takeWhile(_ == '\n').length
    preTrimmed.substring(0, preTrimmed.length - endingNewlines)
  }
}
