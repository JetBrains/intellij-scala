package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * User: Dmitry.Naydanov
  * Date: 08.02.17.
  */
class WorksheetInterpretExprsIterator(file: ScalaFile, ifEditor: Option[Editor], lastProcessed: Option[Int]) {
  private val start = ((lastProcessed, ifEditor) match {
    case (Some(lineNum), Some(editor)) => Some(editor.getDocument getLineEndOffset lineNum)
    case _ => None
  }) flatMap (i => Option(file findElementAt i)) getOrElse file.getFirstChild   
  
  
  def collectAll(acc: PsiElement => Unit, onError: Option[PsiErrorElement => Unit]) {
    var current = start
    
    while (current != null) {
      current match {
        case _: PsiWhiteSpace | _: PsiComment =>
        case error: PsiErrorElement => onError.foreach {
          a => 
            a(error)
            return
        }
        case scalaPsi: ScalaPsiElement => acc(scalaPsi)
        case _ =>
      }
      
      current = current.getNextSibling
    }
  }
}
