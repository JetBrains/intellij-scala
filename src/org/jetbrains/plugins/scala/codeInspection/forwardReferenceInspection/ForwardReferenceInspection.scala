package org.jetbrains.plugins.scala
package codeInspection.forwardReferenceInspection

import com.intellij.codeInspection.ProblemsHolder
import codeInspection.AbstractInspection
import lang.psi.api.expr.ScReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.toplevel.typedef.ScMember
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.statements.{ScVariable, ScValue}
import lang.resolve.ScalaResolveResult
import lang.psi.ScalaPsiUtil

/**
 * Pavel Fatin
 */

class ForwardReferenceInspection extends AbstractInspection {

  def actionFor(holder: ProblemsHolder) = {
    case ref: ScReferenceExpression =>
      val member: ScMember = PsiTreeUtil.getParentOfType(ref, classOf[ScMember])
      if (member != null) {
        member.getContext match {
          case tb: ScTemplateBody if member.isInstanceOf[ScValue] || member.isInstanceOf[ScVariable] =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                ScalaPsiUtil.nameContext(r.getActualElement) match {
                  case resolved if resolved.isInstanceOf[ScValue] || resolved.isInstanceOf[ScVariable] =>
                    if (resolved.getParent == tb && resolved.getTextOffset > member.getTextOffset) {
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
