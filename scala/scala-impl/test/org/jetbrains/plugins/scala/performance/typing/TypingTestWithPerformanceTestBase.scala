package org.jetbrains.plugins.scala.performance.typing

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.File
import scala.concurrent.duration.Duration

abstract class TypingTestWithPerformanceTestBase extends ScalaFixtureTestCase {

  def doFileTest(stringsToType: String*)(implicit timeout: Duration): Unit = {
    val fileName = getTestName(true) + ".scala"
    val filePath = folderPath + fileName
    val ioFile = new File(filePath)
    val fileText = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    val (input, expectedOpt) = separateText(fileText)
    doTest(stringsToType)(input, expectedOpt.orNull)
  }

  def doTest(stringsToType: Seq[String])(input: String, expectedOutput: String)(implicit timeout: Duration): Unit = {
    val fileName = getTestName(true) + ".scala"
    val testName = s"TypingTest${getTestName(false)}"

    myFixture.configureByText(fileName, input.withNormalizedSeparator.trim)

    val testBody: ThrowableRunnable[_] = () => {
      stringsToType.foreach(myFixture.`type`)
      PsiDocumentManager.getInstance(myFixture.getProject).commitAllDocuments()
      if (expectedOutput != null) {
        myFixture.checkResult(expectedOutput.withNormalizedSeparator.trim)
        myFixture.configureByText(fileName, input.withNormalizedSeparator.trim) //reset fixture file text
      }
    }

    PlatformTestUtil
      .startPerformanceTest(testName, timeout.toMillis.toInt, testBody)
      .assertTiming()
  }

  def doTest(stringToType: String)(input: String, expected: String)(implicit timeout: Duration): Unit =
    doTest(Seq(stringToType))(input, expected)

  def doTest(charsToType: Char*)(input: String, expected: String)(implicit timeout: Duration, d: DummyImplicit): Unit =
    doTest(Seq(charsToType.mkString("")))(input, expected)

  protected def folderPath: String = TestUtils.getTestDataPath + "/typing/"

  protected def separateText(fileText: String): (String, Option[String]) = {
    fileText.indexOf("-----") match {
      case -1 => (fileText, None)
      case other =>
        val (before, after) = fileText.splitAt(other)
        (before, Some(after.dropWhile(c => c == '-' || c == '\n')))
    }
  }
}
