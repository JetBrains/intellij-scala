package org.jetbrains.plugins.scala.performance.typing

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 10/29/15.
  */
abstract class TypingPerformanceTestBase extends ScalaFixtureTestCase {
  def doTest(stringsToType: List[String], timeoutInMillis: Int) {
    val fileName = getTestName(false) + ".scala"
    val filePath = folderPath + fileName
    val ioFile = new File(filePath)
    var fileText: String = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    myFixture.configureByText(fileName, fileText)
    PlatformTestUtil.startPerformanceTest("TypingTest" + getTestName(false), timeoutInMillis, new ThrowableRunnable[Nothing] {
      override def run(): Unit = {
        stringsToType.foreach(myFixture.`type`)
      }
    }).ioBound().assertTiming()
  }

  protected def folderPath: String = TestUtils.getTestDataPath + "/typingPerformance/"
}
