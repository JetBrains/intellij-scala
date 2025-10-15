package org.jetbrains.plugins.scala.intelliLang
package injection

import com.intellij.openapi.util.text.StringUtil
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.intellij.lang.regexp.RegExpLanguage
import org.jetbrains.plugins.scala.FileSetTests
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import java.nio.file.{Files, Path}
import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
@Category(Array(classOf[FileSetTests]))
class RegexpLanguageInjectionTest extends ScalaLanguageInjectionTestBase {

  private def regexTestDataDir: Path =
    Path.of("scala", "integration", "intellilang", "testData", "language_injection", "regex")

  @unused("used reflectively by the @Parameters annotation")
  private def testParameters: Array[AnyRef] =
    regexTestDataDir.children().toArray.flatMap(collectFileTests)

  @Test
  @Parameters(method = "testParameters")
  @TestCaseName(value = "{method}[{0}]")
  def regexpLanguageInjectionTest(
    @unused("used reflectively by the @TestCaseName annotation") testName: String,
    testFile: Path,
    testIdx: Int
  ): Unit = {
    val ParsedTestCase(input, expectedResult, testLine) = readTestCaseContent(testFile, testIdx)
    printFileOnFailure(testFile, testLine) {
      doRegexTest(input, expectedResult)
    }
  }

  private def doRegexTest(text: String, injectedFileExpectedText: String): Unit = {
    scalaInjectionTestFixture.doTest(RegExpLanguage.INSTANCE.getID, text, injectedFileExpectedText)
  }

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

  private def collectFileTests(file: Path): Array[AnyRef] = {
    val commonPrefix = regexTestDataDir.toCanonicalPath.toString
    val testCases = readTestCasesRawContents(file, includeContent = false)
    testCases.zipWithIndex.map { case (RawTestCase(_, descriptionOpt, _), testIdx) =>
      val suffixWithIndex = if (testIdx == 0) "" else "-" + testIdx
      val suffixWithDescription = suffixWithIndex + descriptionOpt.fold("")(" " + _)
      val testName = file.toCanonicalPath.toString.stripPrefix(commonPrefix).stripPrefix(java.io.File.separator) + suffixWithDescription
      Array(testName, file, testIdx)
    }.toArray
  }


  // test line for easy navigating from failed tests
  private case class ParsedTestCase(before: String, expectedAfter: String, testLine: Int)
  private case class RawTestCase(content: String, description: Option[String], testLine: Int)

  private def readTestCaseContent(testFile: Path, testIdx: Int): ParsedTestCase = {
    val testCaseText = readTestCaseRawContent(testFile, testIdx)
    val Array(input, expectedResult0) = TestCaseInnerSeparator.split(testCaseText.content)

    // To support trailing space in the end of the line inside test data files. Otherwise it's trimmed by IDE.
    // Example: `some text `
    val expectedResult = expectedResult0.replaceAll("<trailing_space>([\r\n]|$)", "$1")

    ParsedTestCase(input, expectedResult, testCaseText.testLine)
  }

  private def readTestCaseRawContent(testFile: Path, testIdx: Int): RawTestCase = {
    val testCases = readTestCasesRawContents(testFile)
    val head = testCases.drop(testIdx).headOption
    head.getOrElse(fail(s"no test with index $testIdx found in test file $testFile").asInstanceOf[Nothing])
  }

  private def readTestCasesRawContents(testFile: Path, includeContent: Boolean = true): Seq[RawTestCase] = {
    val fileContent = StringUtil.convertLineSeparators(Files.readString(testFile))
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
  private def printFileOnFailure[T](file: Path, line: Int)(body: => T) = try body catch {
    case error: Throwable =>
      System.err.println(s"### Test file: ${file.toCanonicalPath}:${line + 1}") // line is 0-based
      throw error
  }
}
