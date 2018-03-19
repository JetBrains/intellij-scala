package org.jetbrains.plugins.scala.lang.refactoring.move.members

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}

class ScalaMoveMembersHandler extends MoveHandlerDelegate {

  override def tryToMove(element: PsiElement, project: Project, dataContext: DataContext, reference: PsiReference, editor: Editor): Boolean = {
    if (!element.getLanguage.equals(ScalaLanguage.INSTANCE)) {
      false
    } else {
      element match {
        case _: ScObject => false
        case el: ScMember  =>
          Option(PsiTreeUtil.getParentOfType(el, classOf[ScObject])) match {
            case Some(scObj) =>
              val dialog = new ScalaMoveMembersDialog(project, true, scObj, el)
              dialog.show()
              true
            case None => false
          }
        case _ => false
      }
    }
  }

}
