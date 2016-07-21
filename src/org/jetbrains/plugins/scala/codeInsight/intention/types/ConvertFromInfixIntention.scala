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
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScReferenceableInfixTypeElement}

/** Converts type element `(A @@ B)` to `@@[A, B]` */
class ConvertFromInfixIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Use Prefix Type Syntax"

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    element match {
      case Parent(Both(_: ScStableCodeReferenceElement, Parent(Parent(_: ScReferenceableInfixTypeElement)))) => true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val infixTypeElement = PsiTreeUtil.getParentOfType(element, classOf[ScReferenceableInfixTypeElement], false)
    val elementToReplace = infixTypeElement.getParent match {
      case x: ScParenthesisedTypeElement => x
      case _ => infixTypeElement
    }

    if (element == null) return
    infixTypeElement.computeDesugarizedType match {
      case Some(replacement) =>
        elementToReplace.replace(replacement)
        UndoUtil.markPsiFileForUndo(replacement.getContainingFile)
      case _ =>
    }
  }
}
