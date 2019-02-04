package org.jetbrains.plugins.scala
package codeInsight
package intention
package stringLiteral

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
  * User: Dmitry Naydanov
  * Date: 3/31/12
  */
final class InsertGapIntoStringIntention extends PsiElementBaseIntentionAction {

  import InsertGapIntoStringIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    replacement(element.getNode).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    replacement(element.getNode).foreach {
      case (text, caretMove) => inWriteAction {
        val caretModel = editor.getCaretModel
        editor.getDocument.insertString(caretModel.getOffset, text)
        caretModel.moveCaretRelatively(caretMove, 0, false, false, false)
      }
    }

  override def getFamilyName: String = "Insert gap"

  override def getText: String = "Insert gap with concatenation: (\" +  + \")"
}

object InsertGapIntoStringIntention {

  import lang.lexer.ScalaTokenTypes.{tMULTILINE_STRING => MultilineString, tSTRING => RegularString}

  private def replacement(node: ASTNode) = node.getElementType match {
    case RegularString => Some("\" +  + \"", 4)
    case MultilineString => Some("\"\"\" +  + \"\"\"", 6)
    case _ => None
  }
}
