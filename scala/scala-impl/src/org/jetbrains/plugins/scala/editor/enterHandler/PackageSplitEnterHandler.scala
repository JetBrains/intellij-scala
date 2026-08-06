package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.{EnterHandlerDelegate, EnterHandlerDelegateAdapter}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.editor.enterHandler.PackageSplitEnterHandler._

import scala.util.matching.Regex

class PackageSplitEnterHandler extends EnterHandlerDelegateAdapter {
  override def preprocessEnter(file: PsiFile,
                               editor: Editor,
                               caretOffset: Ref[Integer],
                               caretAdvance: Ref[Integer],
                               dataContext: DataContext,
                               originalHandler: EditorActionHandler
                              ): EnterHandlerDelegate.Result = {
    val document = editor.getDocument
    val offset = caretOffset.get()

    process(document, offset) match {
      case Processed(caretOffsetNew, caretShift) =>
        caretOffset.set(caretOffsetNew)
        caretAdvance.set(caretAdvance.get + caretShift)
      case _ =>
    }

    Result.Continue
  }
}


object PackageSplitEnterHandler {
  private val PackageLiteral = "package"
  private val Semicolon = ";"
  private val Dot = '.'
  private val PackageRegex: Regex = s"(\\s*)$PackageLiteral .+".r

  sealed trait ProcessResult
  /**
   * @param caretOffsetNew adjusted caret offset value that will be used in subsequent enter handlers
   * @param caretShift     extra caret shift applied after enter handling
   */
  case class Processed(caretOffsetNew: Int, caretShift: Int) extends ProcessResult
  object NotProcessed extends ProcessResult

  def process(document: Document,
              caretOffset: Int,
              processAtAnyChar: Boolean = false,
              addExtraNewLine: Boolean = false
             ): ProcessResult = {
    val lineNumber = document.getLineNumber(caretOffset)
    val start = document.getLineStartOffset(lineNumber)
    val end = document.getLineEndOffset(lineNumber)

    val line = document.getText(new TextRange(start, end))

    line match {
      case PackageRegex(prefix) =>
        val caretIndex = caretOffset - start
        val dotIndex = line.indexOf(Dot, caretIndex)
        val needToProcess = dotIndex == caretIndex || (processAtAnyChar && dotIndex != -1)

        if (needToProcess) {
          val tail = line.substring(dotIndex + 1)
          val dotOffset = start + dotIndex
          val extraNewLine = if (addExtraNewLine) "\n" else ""
          document.replaceString(dotOffset, end, s"$prefix$extraNewLine$PackageLiteral $tail")

          // if original package statement ended with semicolon then split statement should contain it as well
          val caretOffsetNew =
            if (line.contains(Semicolon) && dotOffset > 0) {
              document.insertString(dotOffset, Semicolon)
              dotOffset + 1
            } else {
              dotOffset
            }

          Processed(caretOffsetNew + extraNewLine.length, caretShift = PackageLiteral.length + 1)
        } else {
          NotProcessed
        }
      case _ =>
        NotProcessed
    }
  }
}