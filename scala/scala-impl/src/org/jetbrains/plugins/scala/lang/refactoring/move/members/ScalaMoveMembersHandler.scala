package org.jetbrains.plugins.scala.lang.refactoring.move.members

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

class ScalaMoveMembersHandler extends MoveHandlerDelegate {

  override def tryToMove(element: PsiElement, project: Project, dataContext: DataContext, reference: PsiReference, editor: Editor): Boolean = {
    element match {
      case _: ScTypeDefinition |
           _: ScClassParameter => false
      case el: ScMember =>
        el.containingClass match {
          case scObj: ScObject =>
            val dialog = new ScalaMoveMembersDialog(project, true, scObj, el)
            dialog.show()
          case _ =>
            val message = ScalaBundle.message("move.members.supported.only.objects")
            val refactoringName = "Move members"
            ScalaRefactoringUtil.showErrorHint(message, refactoringName, null)(project, editor)
        }
        true
      case _ => false
    }

  }

}
