package org.jetbrains.plugins.scala
package codeInsight
package intention
package expression

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement


/**
 * Nikolay.Tropin
 * 4/29/13
 */
final class RemoveUnnecessaryParenthesesIntention extends PsiElementBaseIntentionAction {

  import ParenthesizedElement.Ops

  override def getText: String = InspectionBundle.message("remove.unnecessary.parentheses.fix", "")

  override def getFamilyName: String = getText

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    !isInspectionEnabledIn(project, "ScalaUnnecessaryParentheses") &&
      Option(PsiTreeUtil.getParentOfType(element, classOf[ScParenthesizedElement], false))
        .exists(_.isParenthesisRedundant)
  }

  private def isInspectionEnabledIn(project: Project, id: String) =
    Option(HighlightDisplayKey.find(id)).exists(
      InspectionProjectProfileManager.getInstance(project).getCurrentProfile.isToolEnabled)

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    Option(PsiTreeUtil.getParentOfType(element, classOf[ScParenthesizedElement])) map {
      case expr if expr.isNestedParenthesis => invoke(project, editor, expr)
      case expr if expr.isParenthesisRedundant =>
        inWriteAction {
          expr.doStripParentheses()
        }
      case _ =>
    }
  }
}
