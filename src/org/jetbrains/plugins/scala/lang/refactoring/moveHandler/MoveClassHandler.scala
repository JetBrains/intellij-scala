package org.jetbrains.plugins.scala
package lang
package refactoring
package moveHandler

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.DataConstantsEx
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDirectory, PsiElement, PsiReference}
import com.intellij.openapi.project.Project
import com.intellij.refactoring.move.{MoveCallback, MoveHandlerDelegate}
import psi.api.toplevel.typedef.ScTypeDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.12.2008
 */

class MoveClassHandler extends MoveHandlerDelegate {
  override def canMove(elements: Array[PsiElement], targetContainer: PsiElement): Boolean = {
    super.canMove(elements, targetContainer) && elements.forall(isValidSource)
  }

  override def doMove(project: Project, elements: Array[PsiElement],
                      targetContainer: PsiElement, callback: MoveCallback) {
    MoveRefactoringUtil.moveClass(project, elements, targetContainer, callback)
  }

  override def isValidTarget(psiElement: PsiElement): Boolean = psiElement match {
    case _: PsiDirectory => true
    case _ => false
  }

  private def isValidSource(e: PsiElement): Boolean = e match {
    case t: ScTypeDefinition if t.isTopLevel => true
    case _ => false
  }

  override def tryToMove(element: PsiElement, project: Project,
                         dataContext: DataContext, reference: PsiReference, editor: Editor): Boolean = {
    val valid = isValidSource(element)
    if (valid) {
      MoveRefactoringUtil.moveClass(project, Array[PsiElement](element),
        dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT).asInstanceOf[PsiElement], null)
    } 
    valid
  }
}