package org.jetbrains.plugins.scala.lang.parser

import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiFileFactory
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.parser.PsiFileFactoryTest.generateLargeScalaFileText
import org.junit.Assert.assertEquals

import scala.util.Random

class PsiFileFactoryTest extends ScalaLightCodeInsightFixtureTestCase {

  /**
   * @see [[org.jetbrains.plugins.scala.lang.parser.LargeFilesAnnotatorTest.testShowEditorWarningInLargeScalaFiles]]
   */
  def testTreatTooLargeScalaFilesAsPlainTextFiles(): Unit = {
    val text = generateLargeScalaFileText
    val psiFactory = PsiFileFactory.getInstance(getProject)
    val psiFile = psiFactory.createFileFromText("Dummy.scala", ScalaLanguage.INSTANCE, text)

    assertEquals(text, psiFile.getText)
    assertEquals("Expected file to be treated as plain text file", PlainTextLanguage.INSTANCE, psiFile.getLanguage)
  }
}

object PsiFileFactoryTest {

  /**
   * @see [[com.intellij.psi.SingleRootFileViewProvider.isTooLargeForIntelligence]]
   */
  def generateLargeScalaFileText: String = {
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