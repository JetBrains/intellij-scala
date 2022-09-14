package org.jetbrains.plugins.scala.lang.refactoring.move.members

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

class ScalaMoveMembersHandler extends MoveHandlerDelegate {

  override def tryToMove(element: PsiElement, project: Project, dataContext: DataContext, reference: PsiReference, editor: Editor): Boolean = {
    element match {
      case _: ScTypeDefinition | _: ScClassParameter => false
      case NotSupportedMember(message) =>
        val refactoringName = ScalaBundle.message("move.members")
        ScalaRefactoringUtil.showErrorHint(message.nls, refactoringName, null)(project, editor)
        true
      case objectMember(obj, member) =>
        val dialog = new ScalaMoveMembersDialog(project, true, obj, member)
        dialog.show()
        true
      case _ => false
    }
  }

  private object objectMember {
    def unapply(member: ScMember): Option[(ScObject, ScMember)] =
      member.containingClass.asOptionOf[ScObject].map((_, member))
  }

  private object NotSupportedMember {
    def unapply(member: ScMember): Option[NlsString] = {
      if (ScalaPsiUtil.hasImplicitModifier(member))
        Some(NlsString(ScalaBundle.message("move.members.not.supported.implicits")))

      else if (!hasStablePath(member))
        Some(NlsString(ScalaBundle.message("move.members.supported.only.stable.objects")))

      else if (member.hasModifierProperty("override"))
        Some(NlsString(ScalaBundle.message("move.members.not.supported.overridden")))

      else None
    }


    private def hasStablePath(member: ScMember): Boolean = member.containingClass match {
      case obj: ScObject => ScalaPsiUtil.hasStablePath(obj)
      case _             => false
    }
  }
}
