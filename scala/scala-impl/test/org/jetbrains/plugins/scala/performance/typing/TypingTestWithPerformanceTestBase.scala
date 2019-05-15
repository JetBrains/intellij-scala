package org.jetbrains.plugins.scala.performance.typing

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

import scala.concurrent.duration.Duration

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 10/29/15.
 */
abstract class TypingTestWithPerformanceTestBase extends ScalaFixtureTestCase {

  def doTest(stringsToType: String*)(implicit timeout: Duration) {
    val fileName = getTestName(true) + ".scala"
    val filePath = folderPath + fileName
    val ioFile = new File(filePath)
    val fileText = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8).withNormalizedSeparator
    val (input, expectedOpt) = separateText(fileText)
    doTest(input, expectedOpt.orNull, stringsToType)
  }

  def doTest(input: String, expected: String, stringsToType: Seq[String])
            (implicit timeout: Duration): Unit = {
    val fileName = getTestName(true) + ".scala"
    val testName = s"TypingTest${getTestName(false)}"

    myFixture.configureByText(fileName, input)

    val testBody: ThrowableRunnable[_] = () => {
      stringsToType.foreach(myFixture.`type`)
      PsiDocumentManager.getInstance(myFixture.getProject).commitAllDocuments()
      if (expected != null) {
        val actual = myFixture.getFile.getText.trim
        assertEquals(expected, actual)
        myFixture.configureByText(fileName, input) //reset fixture file text
      }
    }

    PlatformTestUtil
      .startPerformanceTest(testName, timeout.toMillis.toInt, testBody)
      .ioBound()
      .assertTiming()
  }

  def doTest(input: String, expected: String, stringToType: String)(implicit timeout: Duration): Unit =
    doTest(input, expected, Seq(stringToType))

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
