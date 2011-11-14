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
import lang.psi.api.base.{ScStableCodeReferenceElement}
import lang.psi.api.base.types.{ScParenthesisedTypeElement, ScTypeArgs, ScParameterizedTypeElement, ScTypeElement}

/** Converts type element `@@[A, B]` to `(A @@ B)` */
class ConvertToInfixIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert Type"

  override def getText = "Use Infix Type Syntax"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case Parent(Both(ref: ScStableCodeReferenceElement, Parent(Parent(param: ScParameterizedTypeElement))))
       if param.typeArgList.typeArgs.size == 2 && !ref.refName.forall(_.isLetterOrDigit)  => true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val paramTypeElement: ScParameterizedTypeElement = PsiTreeUtil.getParentOfType(element, classOf[ScParameterizedTypeElement], false)
    if (element == null) return
    val Seq(targ1, targ2) = paramTypeElement.typeArgList.typeArgs
    val needParens = paramTypeElement.getParent match {
      case _: ScTypeArgs | _: ScParenthesisedTypeElement => false
      case _ => true
    }
    val newTypeText = Seq(targ1, paramTypeElement.typeElement, targ2).map(_.getText).mkString(" ").parenthesisedIf(needParens)
    val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newTypeText, element.getManager)
    paramTypeElement.replace(newTypeElement)
    UndoUtil.markPsiFileForUndo(newTypeElement.getContainingFile)
  }
}
