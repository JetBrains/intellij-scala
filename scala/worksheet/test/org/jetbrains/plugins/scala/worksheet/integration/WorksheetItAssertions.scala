package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.Folding
import org.junit.Assert.{assertEquals, assertNotNull, fail}

trait WorksheetItAssertions {
  self: WorksheetIntegrationBaseTest =>

  def assertFoldings(expectedFoldings: Seq[Folding], actualFoldings: Seq[Folding]): Unit =
    expectedFoldings.zipAll(actualFoldings, null, null).toList.foreach { case (expected, actual) =>
      assertNotNull(
        s"""there are to few actual foldings:
           |expected : $expected
           |expected all : $expectedFoldings
           |actual all : $actualFoldings
           |""".stripMargin,
        actual
      )
      assertNotNull(
        s"""there are some unexpected foldings:
           |actual: $actual
           |expected all : $expectedFoldings
           |actual all : $actualFoldings
           |""".stripMargin,
        expected
      )
      assertEquals(expected, actual)
    }

  // This is not the most fine-grained testing but quite easy to produce new test data,
  // just copying from actual messages window (though should be checked manually first)
  protected def assertCompilerMessages(editor: Editor)(expectedAllMessagesText: String): Unit = {
    val messages = collectedCompilerMessages(editor)
    val messagesRendered = messages.map { message: CompilerMessageImpl =>
      val level = message.getCategory.toString.toLowerCase.capitalize
      s"$level:(${message.getLine}, ${message.getColumn}) ${message.getMessage}"
    }
    val actualAllMessagesText = messagesRendered.mkString("\n\n")
    assertEquals(
      expectedAllMessagesText.withNormalizedSeparator,
      actualAllMessagesText
    )
  }


  def assertCompiledWithoutErrors(editor: Editor): Unit =
    assertCompiledWithoutMessages(editor, CompilerMessageCategory.ERROR)

  def assertCompiledWithoutWarnings(editor: Editor): Unit =
    assertCompiledWithoutMessages(editor, CompilerMessageCategory.WARNING)

  def assertCompiledWithoutMessages(editor: Editor, category: CompilerMessageCategory): Unit = {
    val messages = collectedCompilerMessages(editor).filter(_.getCategory == category)
    if (messages.nonEmpty) {
      val messagesRenders = messages.map { err =>
        s"${err.getCategory} (${err.getLine}, ${err.getColumn}) ${err.getMessage}}"
      }
      val typ = category match {
        case CompilerMessageCategory.ERROR       => "errors"
        case CompilerMessageCategory.WARNING     => "warnings"
        case CompilerMessageCategory.INFORMATION => "information messages"
        case CompilerMessageCategory.STATISTICS  => "???"
      }
      fail(s"Unexpected compilation $typ occurred during worksheet evaluation:\n${messagesRenders.mkString("\n")}")
    }
  }
}
