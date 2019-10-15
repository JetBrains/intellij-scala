package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class WorksheetInterpretExprsIterator(file: ScalaFile, editorOpt: Option[Editor], lastProcessedLine: Option[Int]) {

  private val startPsiElement: PsiElement = {
    val element = for {
      line         <- lastProcessedLine
      editor       <- editorOpt
      (start, end) = lineRange(line, editor)
      element      <- Option(file.findElementAt((start + end) / 2))
    } yield element

    element.getOrElse(file.getFirstChild)
  }

  private def lineRange(lineIdx: Int, editor: Editor): (Int, Int) = {
    val document = editor.getDocument
    (document.getLineStartOffset(lineIdx), document.getLineEndOffset(lineIdx))
  }

  // TODO: rewrite this in a more functional streaming way
  //  rewrite WorksheetPsiGlue that add and then removes elements to store
  def collectAll(acc: PsiElement => Unit, onError: Option[PsiErrorElement => Unit]): Unit = {
    var current = inReadAction {
      startPsiElement.parentsInFile.lastOption.getOrElse(startPsiElement)
    }

    if (lastProcessedLine.isDefined)
      current = current.getNextSibling
    
    while (current != null) {
      current match {
        case error: PsiErrorElement =>
          onError.foreach { handler =>
            handler(error)
            return
          }
        case other =>
          acc(other)
      }

      current = current.getNextSibling
    }
  }
}
