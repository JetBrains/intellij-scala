package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiElement
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import extensions._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.command.undo.UndoUtil
import lang.psi.api.base.ScStableCodeReferenceElement
import lang.psi.api.base.types.{ScParenthesisedTypeElement, ScInfixTypeElement}

/** Converts type element `(A @@ B)` to `@@[A, B]` */
class ConvertFromInfixIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert Type"

  override def getText = "Use Prefix Type Syntax"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case Parent(Both(ref: ScStableCodeReferenceElement, Parent(Parent(param: ScInfixTypeElement)))) => true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixTypeElement: ScInfixTypeElement = PsiTreeUtil.getParentOfType(element, classOf[ScInfixTypeElement], false)
    val elementToRelace = infixTypeElement.getParent match {
      case x: ScParenthesisedTypeElement => x
      case _ => infixTypeElement
    }

    if (element == null) return
    val newTypeText = infixTypeElement.ref.getText + "[" +infixTypeElement.lOp.getText + ", " + infixTypeElement.rOp.map(_.getText).getOrElse("") + "]"
    val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newTypeText, element.getManager)
    elementToRelace.replace(newTypeElement)
    UndoUtil.markPsiFileForUndo(newTypeElement.getContainingFile)
  }
}
