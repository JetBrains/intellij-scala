package org.jetbrains.plugins.scala
package codeInsight.intention.literal

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import lang.lexer.ScalaTokenTypes
import com.intellij.psi.{PsiDocumentManager, PsiElement}

/**
 * User: Dmitry Naydanov
 * Date: 3/31/12
 */

class InsertGapIntoStringIntention extends PsiElementBaseIntentionAction {
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  def getFamilyName: String = "Insert gap"

  override def getText: String = "Insert gap with concatenation: (\" +  + \")"


  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = element != null && 
    element.getNode != null && Set(tSTRING, tMULTILINE_STRING).contains(element.getNode.getElementType)

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    def insertString(str: String, caretMove: Int) {
      extensions.inWriteAction {
        editor.getDocument.insertString(editor.getCaretModel.getOffset, str)
        editor.getCaretModel.moveCaretRelatively(caretMove, 0, false, false, false)
      }
    }

    element.getNode.getElementType match {
      case ScalaTokenTypes.tSTRING => insertString("\" +  + \"", 4)
      case ScalaTokenTypes.tMULTILINE_STRING => insertString("\"\"\" +  + \"\"\"", 6)
      case _ =>
    }
  }
}
