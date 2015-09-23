package org.jetbrains.plugins.scala.codeInspection.delayedInit

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionsUtil}
import org.jetbrains.plugins.scala.extensions.{Both, ChildOf}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Nikolay.Tropin
 */
class FieldFromDelayedInitInspection extends AbstractInspection("FieldFromDelayedInit", "Field from DelayedInit"){
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case ref: ScReferenceExpression =>
      ref.bind() match {
        case Some(FieldInDelayedInit(tb)) =>
          if (!tb.isAncestorOf(ref))
            holder.registerProblem(ref.nameId, "Field defined in DelayedInit is likely to be null")
        case _ =>
      }
  }

  object FieldInDelayedInit {
    def unapply(srr: ScalaResolveResult): Option[ScTemplateBody] = {
      ScalaPsiUtil.nameContext(srr.getElement) match {
        case Both(ChildOf(tb: ScTemplateBody), (_: ScPatternDefinition | _: ScVariableDefinition)) =>
          if (srr.fromType.exists(InspectionsUtil.conformsToTypeFromClass(_, "scala.DelayedInit", tb.getProject))) Some(tb)
          else None
        case _ => None
      }
    }
  }
}
