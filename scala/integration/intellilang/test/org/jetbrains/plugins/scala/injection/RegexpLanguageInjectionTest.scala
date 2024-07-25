package org.jetbrains.plugins.scala
package injection

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ThrowableRunnable
import junit.framework.{TestCase, TestSuite}
import org.intellij.lang.regexp.RegExpLanguage
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.io.File

class RegexpLanguageInjectionTest extends TestCase

object RegexpLanguageInjectionTest {

  //noinspection JUnitMalformedDeclaration
  @Category(Array(classOf[FileSetTests]))
  final class ActualTest(
    testFile: File,
    testName: String,
    testIdx: Int,
  ) extends ScalaLanguageInjectionTestBase {

    override def getTestName(lowercaseFirstLetter: Boolean): String = ""

    override def getName: String = testName

    override def runTestRunnable(testRunnable: ThrowableRunnable[Throwable]): Unit = {
      val ParsedTestCase(input, expectedResult, testLine) = readTestCaseContent(testFile, testIdx)
      printFileOnFailure(testFile, testLine) {
        doRegexTest(input, expectedResult)
      }
    }

    private def doRegexTest(text: String, injectedFileExpectedText: String): Unit = {
      scalaInjectionTestFixture.doTest(RegExpLanguage.INSTANCE.getID, text, injectedFileExpectedText)
    }
  }

  private val regexTestDataDir: File =
    new File("./scala/integration/intellilang/testData/language_injection/regex")

  /**
   * Can include optional test name. First test can be without any header<br>
   * ==Example 1==
   * {{{
   * test case data 1
   * #
   * test case data 2
   * ### test name
   * test case data 3
   * }}}
   * ==Example 2==
   * {{{
   * # test name 1
   * test case data 1
   * # test name 2
   * test case data 2
   * }}}
   */
  private val NewTestCaseHeader = "(^|\\n)#+([^\\r\\n]*)\\r?\\n".r

  private val TestCaseInnerSeparator = "\\n---+\\r?\\n".r

  final def suite: junit.framework.Test = {
    // suite name will be automatically to set to class name by org.junit.runners.AllTests
    val suite = new TestSuite()

    val files = regexTestDataDir.listFiles()
    val allTests = files.flatMap(collectFileTests)
    allTests.foreach(suite.addTest)

    suite
  }

  private def collectFileTests(file: File): Seq[ActualTest] = {
    val commonPrefix = regexTestDataDir.getAbsolutePath

    val testCases = readTestCasesRawContents(file, includeContent = false)
    testCases.zipWithIndex.map { case (RawTestCase(_, descriptionOpt, _), testIdx) =>
      val suffixWithIndex = if (testIdx == 0) "" else "-" + testIdx
      val suffixWithDescription = suffixWithIndex + descriptionOpt.fold("")(" " + _)
      val testName = file.getAbsolutePath.stripPrefix(commonPrefix).stripPrefix(File.separator) + suffixWithDescription

      new ActualTest(file, testName, testIdx)
    }
  }

  // test line for easy navigating from failed tests
  private case class ParsedTestCase(before: String, expectedAfter: String, testLine: Int)
  private case class RawTestCase(content: String, description: Option[String], testLine: Int)

  private def readTestCaseContent(testFile: File, testIdx: Int): ParsedTestCase = {
    val testCaseText = readTestCaseRawContent(testFile, testIdx)
    val Array(input, expectedResult0) = TestCaseInnerSeparator.split(testCaseText.content)

    // To support trailing space in the end of the line inside test data files. Otherwise it's trimmed by IDE.
    // Example: `some text `
    val expectedResult = expectedResult0.replaceAll("<trailing_space>([\r\n]|$)", "$1")

    ParsedTestCase(input, expectedResult, testCaseText.testLine)
  }

  private def readTestCaseRawContent(testFile: File, testIdx: Int): RawTestCase = {
    val testCases = readTestCasesRawContents(testFile)
    val head = testCases.drop(testIdx).headOption
    head.getOrElse(fail(s"no test with index $testIdx found in test file $testFile").asInstanceOf[Nothing])
  }

  private def readTestCasesRawContents(testFile: File, includeContent: Boolean = true): Seq[RawTestCase] = {
    val fileContent = StringUtil.convertLineSeparators(FileUtil.loadFile(testFile))
    val testCases = parseTestCases(fileContent, includeContent)
    assertTrue(s"no test cases found in test file $testFile", testCases.nonEmpty)
    testCases
  }

  private def parseTestCases(text: String, includeContent: Boolean): Seq[RawTestCase] = {
    val allMatches = NewTestCaseHeader.findAllMatchIn(text).toSeq

    // adding two extra phantom blocks to avoid checking edge cases
    val innerBlocks = allMatches.map { m => (m.start, m.end, m.group(2).trim) }
    val startBlock = (0, 0, "")
    val endBlock = (text.length, text.length, "")
    val matchBlocks = startBlock +: innerBlocks :+ endBlock

    val pairs = matchBlocks.sliding(2).toSeq
    pairs.flatMap { case Seq((start1, end1, description), (start2, _, _)) =>
      val isFirstPhantomBlock = start1 == start2
      if (isFirstPhantomBlock) None else {
        val content = if (includeContent) text.substring(end1, start2) else ""

        val testLine =
          if (start1 == 0) 0
          else StringUtil.countChars(text, '\n', 0, start1, false) + 1 // (non-first regex match also captures new line)

        val testCase = RawTestCase(content, Some(description).filter(_.nonEmpty), testLine)
        Some(testCase)
      }
    }
  }

  // print file path with line number to be able to Ctrl + Click in console to navigate to test file on failure (see IDEA-257969)
  private def printFileOnFailure[T](file: File, line: Int)(body: => T) = try body catch {
    case error: Throwable =>
      System.err.println(s"### Test file: ${file.getAbsolutePath}:${line + 1}") // line is 0-based
      throw error
  }
}
