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
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScParenthesisedTypeElement, ScTypeArgs}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/** Converts type element `@@[A, B]` to `(A @@ B)` */
class ConvertToInfixIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Use Infix Type Syntax"

  override def getText = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case Parent(Both(ref: ScStableCodeReferenceElement, Parent(Parent(param: ScParameterizedTypeElement))))
       if param.typeArgList.typeArgs.size == 2 && !ref.refName.forall(_.isLetterOrDigit)  => true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (element == null || !element.isValid) return
    val paramTypeElement: ScParameterizedTypeElement = PsiTreeUtil.getParentOfType(element, classOf[ScParameterizedTypeElement], false)
    val Seq(targ1, targ2) = paramTypeElement.typeArgList.typeArgs
    val needParens = paramTypeElement.getParent match {
      case _: ScTypeArgs | _: ScParenthesisedTypeElement => false
      case _ => true
    }
    val newTypeText = Seq(targ1, paramTypeElement.typeElement, targ2).map(_.getText).mkString(" ").parenthesisedIf(needParens)
    val newTypeElement = ScalaPsiElementFactory.createTypeElementFromText(newTypeText, element.getManager)
    if (paramTypeElement.isValid) {
      val replaced = try {
        paramTypeElement.replace(newTypeElement)
      } catch {
        case npe: NullPointerException =>
          throw new RuntimeException("Unable to replace: %s with %s".format(paramTypeElement, newTypeText), npe)
      }
      UndoUtil.markPsiFileForUndo(replaced.getContainingFile)
    }
  }
}
