package org.jetbrains.plugins.scala
package codeInspection
package delayedInit

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.{&&, ContainingClass, LazyVal, PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Nikolay.Tropin
 */
class FieldFromDelayedInitInspection extends AbstractInspection("FieldFromDelayedInit", "Field from DelayedInit") {

  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case ref: ScReferenceExpression =>
      ref.bind() match {
        case Some(FieldInDelayedInit(delayedInitClass)) =>
          val classContainers = ref.parentsInFile.collect {
            case td: ScTemplateDefinition => td
          }
          if (!classContainers.exists(c => c.sameOrInheritor(delayedInitClass)))
            holder.registerProblem(ref.nameId, "Field defined in DelayedInit is likely to be null")
        case _ =>
      }
  }

  object FieldInDelayedInit {
    def unapply(srr: ScalaResolveResult): Option[PsiClass] = {
      ScalaPsiUtil.nameContext(srr.getElement) match {
        case LazyVal(_) => None
        case (_: ScPatternDefinition | _: ScVariableDefinition) && ContainingClass(clazz@(_: ScClass | _: ScObject))
          if srr.fromType.exists(conformsToTypeFromClass(_, "scala.DelayedInit")(clazz)) => Some(clazz)
        case _ => None
      }
    }
  }
}
