package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParenthesisedTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/** Converts type element `(A @@ B)` to `@@[A, B]` */
class ConvertFromInfixIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Use Prefix Type Syntax"

  override def getText = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case Parent(Both(ref: ScStableCodeReferenceElement, Parent(Parent(param: ScInfixTypeElement)))) => true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixTypeElement: ScInfixTypeElement = PsiTreeUtil.getParentOfType(element, classOf[ScInfixTypeElement], false)
    val elementToReplace = infixTypeElement.getParent match {
      case x: ScParenthesisedTypeElement => x
      case _ => infixTypeElement
    }

    if (element == null) return
    val newTypeText = infixTypeElement.ref.getText + "[" +infixTypeElement.lOp.getText + ", " + infixTypeElement.rOp.map(_.getText).getOrElse("") + "]"
    val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newTypeText, element.getManager)
    val replaced = elementToReplace.replace(newTypeElement)
    UndoUtil.markPsiFileForUndo(replaced.getContainingFile)
  }
}
