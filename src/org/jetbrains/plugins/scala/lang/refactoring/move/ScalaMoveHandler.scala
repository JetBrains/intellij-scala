package org.jetbrains.plugins.scala
package lang
package refactoring
package move

import com.intellij.openapi.actionSystem.ex.DataConstantsEx
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDirectory, PsiElement, PsiReference}
import com.intellij.openapi.project.Project
import com.intellij.refactoring.move.{MoveCallback, MoveHandlerDelegate}
import psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.actionSystem.{LangDataKeys, DataContext}

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.12.2008
 */

class ScalaMoveHandler extends MoveHandlerDelegate {
  override def canMove(elements: Array[PsiElement], targetContainer: PsiElement): Boolean = {
    super.canMove(elements, targetContainer) && elements.forall(isValidSource)
  }

  override def doMove(project: Project, elements: Array[PsiElement],
                      targetContainer: PsiElement, callback: MoveCallback) {
    MoveRefactoringUtil.moveClass(project, elements, targetContainer, callback)
  }

  override def isValidTarget(psiElement: PsiElement, sources: Array[PsiElement]): Boolean = psiElement match {
    case _: PsiDirectory => true
    case _ => false
  }

  private def isValidSource(e: PsiElement): Boolean = e match {
    case t: ScTypeDefinition if t.isTopLevel => true
    case _ => false
  }

  override def tryToMove(element: PsiElement, project: Project,
                         dataContext: DataContext, reference: PsiReference, editor: Editor): Boolean = {
    //todo: check move target: if it's source dir, then move class, otherwise move file.
    val valid = isValidSource(element)
    if (valid) {
      MoveRefactoringUtil.moveClass(project, Array[PsiElement](element),
        LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext), null)
    } 
    valid
  }
}