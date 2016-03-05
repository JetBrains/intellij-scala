package org.jetbrains.plugins.hocon

import java.io.File

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.hocon.lang.HoconLanguage
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.testcases.FileSetTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

/**
 * @author ghik
 */
object HoconFileSetTestCase {
  val CaretMarker = "<caret>"
}

import org.jetbrains.plugins.hocon.HoconFileSetTestCase._

abstract class HoconFileSetTestCase(subpath: String)
  extends FileSetTestCase(TestUtils.getTestDataPath + "/hocon/" + subpath) {

  protected def transform(data: Seq[String]): String

  protected def preprocessData(parts: Seq[String]): Seq[String] =
    parts

  private def trimNewLines(str: String) = {
    val preTrimmed = str.substring(str.prefixLength(_ == '\n'))
    val endingNewlines = preTrimmed.reverseIterator.takeWhile(_ == '\n').length
    preTrimmed.substring(0, preTrimmed.length - endingNewlines)
  }

  protected def runTest(file: File): Unit = {
    val fileContents = new String(FileUtil.loadFileText(file, "UTF-8")).replaceAllLiterally("\r", "")
    val allParts = preprocessData(fileContents.split("-{5,}").map(trimNewLines).toSeq)
    Assert.assertTrue(allParts.nonEmpty)
    val data = allParts.init
    val expectedResult = allParts.last
    Assert.assertEquals(expectedResult, trimNewLines(transform(data).replaceAllLiterally("\r", "")))
  }

  protected def settings =
    CodeStyleSettingsManager.getInstance(getProject).getCurrentSettings.getCommonSettings(HoconLanguage)

  protected def adjustSettings(): Unit = {
    val indentOptions = settings.getIndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
  }

  override protected def setUp(project: Project): Unit = {
    super.setUp(project)
    adjustSettings()
    getProject
  }

  protected def extractCaret(fileText: String): (String, Int) = {
    val caretOffset = fileText.indexOf(CaretMarker)
    if (caretOffset >= 0)
      (fileText.substring(0, caretOffset) + fileText.substring(caretOffset + CaretMarker.length), caretOffset)
    else
      (fileText, -1)
  }

  protected def insertCaret(fileText: String, caretOffset: Int) =
    if (caretOffset >= 0 && caretOffset <= fileText.length)
      fileText.substring(0, caretOffset) + CaretMarker + fileText.substring(caretOffset)
    else
      fileText

  protected def inWriteCommandAction[T](code: => T): T =
    extensions.inWriteCommandAction(getProject)(code)

  protected def inReadAction[T](code: => T): T =
    extensions.inReadAction(code)
}
