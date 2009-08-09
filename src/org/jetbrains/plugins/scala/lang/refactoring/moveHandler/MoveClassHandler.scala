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
import psi.api.ScalaFile
import psi.api.toplevel.typedef.ScTypeDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.12.2008
 */

class MoveClassHandler extends MoveHandlerDelegate {
  override def canMove(elements: Array[PsiElement], targetContainer: PsiElement): Boolean = {
    for (element <- elements if !element.isInstanceOf[ScTypeDefinition] || !element.getParent.isInstanceOf[ScalaFile]) {
      return false
    }
    return targetContainer == null || isValidTarget(targetContainer)
  }

  override def doMove(project: Project, elements: Array[PsiElement], targetContainer: PsiElement, callback: MoveCallback): Unit = {
    MoveRefactoringUtil.moveClass(project, elements, targetContainer, callback)
  }

  override def isValidTarget(psiElement: PsiElement): Boolean = psiElement match {
    case _: PsiDirectory => true
    case _ => false
  }

  override def tryToMove(element: PsiElement, project: Project,
                        dataContext: DataContext, reference: PsiReference, editor: Editor): Boolean = {
    if (element.isInstanceOf[ScTypeDefinition]) {
      MoveRefactoringUtil.moveClass(project, Array[PsiElement](element),
        dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT).asInstanceOf[PsiElement], null)
      return true
    }
    return false
  }
}