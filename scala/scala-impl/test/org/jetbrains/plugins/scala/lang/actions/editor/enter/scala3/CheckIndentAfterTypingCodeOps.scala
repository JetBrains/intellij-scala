package org.jetbrains.plugins.scala
package lang.actions.editor.enter.scala3

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.TestStringUtils.StringOps
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
abstract class CheckIndentAfterTypingCodeOps extends EditorActionTestBase {

  private implicit def project: Project = myFixture.getProject

  /** @return final code after typing */
  protected def checkIndentAfterTypingCode(
    contextCode: String,
    codeToType: String,
    fixture: JavaCodeInsightTestFixture
  ): String = {
    val indentSize = 2
    val contextCodeIndent = TestIndentUtils.calcLineAtCaretIndent(contextCode)
    val innerCodeIndent = contextCodeIndent + indentSize
    val innerCodeIndentString = " " * innerCodeIndent

    val lines = linesToType(codeToType)

    val stripTrailingSpaces = true

    var contextCodeAtCurrentStep = contextCode
    for {
      (line, lineIdx) <- lines.zipWithIndex
    } {
      val beforeTyping =
        if (lineIdx > 0) {
          val beforeEnter = contextCodeAtCurrentStep

          // 1. after Enter is pressed, caret should be indented from previous line
          // (assuming that test data implies that, e.g. `def foo = <caret>`)
          // 2. if there were whitespaces after the caret before Enter is pressed, they should be removed
          // and the caret should be aligned with the content if there is some
          val expectedAfterEnter = beforeEnter
            .insertStringBeforeCaret("\n" + innerCodeIndentString)
            .removeSpacesAfterCaret

          performTest(beforeEnter, expectedAfterEnter, stripTrailingSpacesAfterAction = stripTrailingSpaces) { () =>
            performEnterAction()
          }
          expectedAfterEnter
        }
        else contextCodeAtCurrentStep

      val expectedAfterTyping = beforeTyping.insertStringBeforeCaret(line)
      if (line.nonEmpty) {
        performTest(beforeTyping, expectedAfterTyping, stripTrailingSpacesAfterAction = stripTrailingSpaces) { () =>
          performTypingAction(line)

          // do not adjust indent for comments (required for tests with comments in the end of indentaion block)
          // example: {{{
          //   def foo =
          //     println(1)
          //     //line comment <caret+Enter>
          // }}}
          // comments in the end of block are not attached to the block, so the formatter moves it to the right
          val isComment = {
            val trimmedLine = line.trim
            trimmedLine.startsWith("//") || trimmedLine.startsWith("/*")
          }
          if (!isComment) {
            adjustLineIndentAtCaretPosition(fixture)
          }
        }
      }

      contextCodeAtCurrentStep = expectedAfterTyping
    }

    contextCodeAtCurrentStep
  }

  /** handles trailing empty line (adds extra empty line in the end) */
  protected def linesToType(codeToType: String): Seq[String] =
    codeToType.linesIterator.toSeq ++ (if (codeToType.endsWith("\n")) Some("") else None)

  protected def adjustLineIndentAtCaretPosition(fixture: JavaCodeInsightTestFixture): Unit = {
    val editor = fixture.getEditor
    val document = editor.getDocument

    val lineOffset = {
      val caretOffset = editor.getCaretModel.getCurrentCaret.getOffset
      val lineNumber = document.getLineNumber(caretOffset)
      document.getLineStartOffset(lineNumber)
    }
    inWriteCommandAction {
      PsiDocumentManager.getInstance(project).commitDocument(document)
      CodeStyleManager.getInstance(project).adjustLineIndent(myFixture.getFile, lineOffset)
    }
  }
}
