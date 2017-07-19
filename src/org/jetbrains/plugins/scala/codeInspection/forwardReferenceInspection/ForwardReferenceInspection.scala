package org.jetbrains.plugins.scala
package codeInspection.forwardReferenceInspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Alefas
 */
class ForwardReferenceInspection extends AbstractInspection {

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case ref: ScReferenceExpression =>
      val member: ScMember = PsiTreeUtil.getParentOfType(ref, classOf[ScMember])
      if (member != null) {
        member.getContext match {
          case tb: ScTemplateBody if member.isInstanceOf[ScValue] || member.isInstanceOf[ScVariable] =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                ScalaPsiUtil.nameContext(r.getActualElement) match {
                  case resolved if resolved.isInstanceOf[ScValue] || resolved.isInstanceOf[ScVariable]=>
                    if (resolved.getParent == tb && !member.hasModifierProperty("lazy") &&
                      !resolved.asInstanceOf[ScMember].hasModifierProperty("lazy") &&
                      resolved.getTextOffset > member.getTextOffset) {
                      holder.registerProblem(ref, ScalaBundle.message("suspicicious.forward.reference.template.body"))
                    }
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
      }
  }
}
