package org.jetbrains.plugins.scala
package codeInspection.forwardReferenceInspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.nameContext
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
  * Alefas
  */
class ForwardReferenceInspection extends AbstractInspection {

  import ForwardReferenceInspection.asValueOrVariable

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case ref: ScReferenceExpression =>
      val maybeMember = ref.parentOfType(classOf[ScMember])
        .collect(asValueOrVariable)
        .filter(_.getContext.isInstanceOf[ScTemplateBody])

      val maybeResolved = ref.bind()
        .map(_.getActualElement)
        .map(nameContext)
        .collect(asValueOrVariable)

      val flag = maybeMember.zip(maybeResolved).exists {
        case (member, resolved) => resolved.getParent == member.getContext && resolved.getTextOffset > member.getTextOffset
      }

      if (flag) {
        holder.registerProblem(ref, ScalaBundle.message("suspicicious.forward.reference.template.body"))
      }
  }
}

object ForwardReferenceInspection {

  private def asValueOrVariable: PartialFunction[PsiElement, ScValueOrVariable] = {
    case v: ScValueOrVariable if !v.hasModifierProperty("lazy") => v
  }
}
