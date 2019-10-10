package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Editor
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class WorksheetInterpretExprsIterator(file: ScalaFile, editorOpt: Option[Editor], lastProcessed: Option[Int]) {

  private val startPsiElement: PsiElement = {
    val element = for {
      lineNum      <- lastProcessed
      editor       <- editorOpt
      (start, end) = lineRange(lineNum, editor)
      el           <- Option(file.findElementAt((start + end) / 2))
    } yield el

    element.getOrElse(file.getFirstChild)
  }

  private def lineRange(lineIdx: Int, editor: Editor): (Int, Int) = {
    val document = editor.getDocument
    (document.getLineStartOffset(lineIdx), document.getLineEndOffset(lineIdx))
  }

  def collectAll(acc: PsiElement => Unit, onError: Option[PsiErrorElement => Unit]): Unit = {
    var current = inReadAction {
      startPsiElement.parentsInFile.lastOption.getOrElse(startPsiElement)
    }

    if (lastProcessed.isDefined)
      current = current.getNextSibling
    
    while (current != null) {
      current match {
        case _: PsiWhiteSpace | _: PsiComment =>
        case error: PsiErrorElement => onError.foreach { handler =>
          handler(error)
          return
        }
        case scalaPsi: ScalaPsiElement => acc(scalaPsi)
        case _ =>
      }
      
      current = current.getNextSibling
    }
  }
}
