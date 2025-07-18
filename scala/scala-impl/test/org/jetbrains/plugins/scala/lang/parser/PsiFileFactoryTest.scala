package org.jetbrains.plugins.scala.lang.parser

import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiFileFactory
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.parser.PsiFileFactoryTest.generateLargeScalaFileWithLargeCommentText
import org.junit.Assert.assertEquals

import scala.util.Random

// See also `org.jetbrains.plugins.scala.PsiFileFactoryForDecompiledScalaFileTest`
class PsiFileFactoryTest extends ScalaLightCodeInsightFixtureTestCase {

  /**
   * @see [[org.jetbrains.plugins.scala.lang.parser.LargeFilesAnnotatorTest.testShowEditorWarningInLargeScalaFiles]]
   */
  def testTreatTooLargeScalaFilesAsPlainTextFiles(): Unit = {
    val scalaFileText = generateLargeScalaFileWithLargeCommentText
    val psiFactory = PsiFileFactory.getInstance(getProject)
    val psiFile = psiFactory.createFileFromText("Dummy.scala", ScalaLanguage.INSTANCE, scalaFileText)

    assertEquals(scalaFileText, psiFile.getText)
    assertEquals("File should be treated as a plain text file (as being a too large file)", PlainTextLanguage.INSTANCE, psiFile.getLanguage)
  }
}

object PsiFileFactoryTest {

  def generateLargeScalaFileText: String = {
    val content = new StringBuilder()
    content.append(
      """package org.example.long.packageName.to.make.the.decompiled.version.larger.faster
        |
        |class DummyScalaClass {
        |""".stripMargin)

    val methodsCount = 50000
    (1 to methodsCount).foreach { idx =>
      content.append(s"def foo$idx: DummyScalaClass = ???\n")
    }

    content.append("\n}")
    content.toString()
  }

  /**
   * @see [[com.intellij.psi.SingleRootFileViewProvider.isTooLargeForIntelligence]]
   */
  def generateLargeScalaFileWithLargeCommentText: String = {
    val content = new StringBuilder()
    content.append("""class DummyScalaClass\n""")
    appendLargeBlockComment(content)
    content.toString()
  }

  private def appendLargeBlockComment(content: StringBuilder): Unit = {
    val commentLinesCount = 50000
    val commentLineLength = 100

    content.append("/*\n")
    for (_ <- 1 to commentLinesCount) {
      content.append(generateRandomComment(commentLineLength)).append("\n")
    }
    content.append("*/")
  }

  private def generateRandomComment(length: Int): String =
    Random.alphanumeric.take(length).mkString
}