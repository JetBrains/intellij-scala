package org.jetbrains.plugins.hocon

import java.io.{File, FileNotFoundException}
import java.util.regex.Pattern

import com.intellij.openapi.application.{ApplicationManager, Result}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.LightPlatformTestCase.getProject
import com.intellij.testFramework.{EditorTestUtil, LightPlatformCodeInsightTestCase, LightPlatformTestCase, ThreadTracker}
import com.intellij.util.LocalTimeCounter
import junit.framework.TestSuite
import org.jetbrains.plugins.hocon.lang.HoconLanguage
import org.jetbrains.plugins.hocon.psi.HoconPsiFile
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, assertTrue}

/**
  * @author ghik
  */
abstract class HoconFileSetTestCase(subpath: String) extends TestSuite {

  protected def transform(data: Seq[String]): String

  protected def preprocessData(parts: Seq[String]): Seq[String] = parts

  import HoconFileSetTestCase._

  files(s"${TestUtils.getTestDataPath}/hocon/$subpath")
    .filter(isValid)
    .map(file => new ActualTest(file))
    .foreach(addTest)

  protected def setUp(): Unit = {
    val settings = CodeStyleSettingsManager.getInstance(LightPlatformTestCase.getProject)
      .getCurrentSettings
      .getCommonSettings(HoconLanguage)

    val indentOptions = settings.getIndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
  }

  override def getName: String = getClass.getName

  private class ActualTest(val testFile: File) extends LightPlatformCodeInsightTestCase {

    override protected def getTestName(lowercaseFirstLetter: Boolean) = ""

    override protected def setUp(): Unit = {
      super.setUp()

      Seq("Timer", "BaseDataReader", "ProcessWaitFor")
        .foreach(longRunningThreadCreated)
    }

    override protected def runTest(): Unit = {
      val fileContents = new String(FileUtil.loadFileText(testFile, "UTF-8")).replaceAllLiterally("\r", "")
      val allParts = preprocessData(fileContents.split("-{5,}").map(trimNewLines).toSeq)
      assertTrue(allParts.nonEmpty)

      val data = allParts.init
      val expectedResult = allParts.last
      assertEquals(expectedResult, trimNewLines(transform(data).replaceAllLiterally("\r", "")))
    }

    override protected def resetAllFields(): Unit = {
      // Do nothing otherwise testFile will be nulled out before getName() is called.
    }

    override def getName: String = testFile.getAbsolutePath

    override def toString: String = s"$getName "
  }

}

object HoconFileSetTestCase {

  private def files(path: String): Seq[File] = new File(path) match {
    case directory if directory.exists() && directory.isDirectory =>
      allFiles(directory)
    case _ => throw new FileNotFoundException(path)
  }

  private[this] def allFiles(file: File): Seq[File] = {
    val files = file.listFiles.toSeq
    files ++ files.filter(_.isDirectory).flatMap(allFiles)
  }

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

  private val pattern = Pattern.compile("(.*)\\.test")

  private def isValid(file: File): Boolean = {
    file.isFile &&
      !file.getName.headOption.contains('_') &&
      pattern.matcher(file.getAbsolutePath).matches()
  }

  private def longRunningThreadCreated(name: String): Unit = {
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication, name)
  }

  private def trimNewLines(string: String) = {
    val start = orDefault(string.indexWhere(isNotNewLine))
    val end = orDefault(string.lastIndexWhere(isNotNewLine), string.length - 1) + 1
    string.substring(start, end)
  }

  private[this] def orDefault(i: Int, default: Int = 0) = i match {
    case -1 => default
    case _ => i
  }

  private[this] def isNotNewLine(char: Char) = char != '\n'
}
