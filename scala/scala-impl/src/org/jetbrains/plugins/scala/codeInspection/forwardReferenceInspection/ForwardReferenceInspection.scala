package org.jetbrains.plugins.scala
package codeInspection
package forwardReferenceInspection

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.nameContext
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}


class ForwardReferenceInspection extends AbstractRegisteredInspection {

  import ForwardReferenceInspection._

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case ref: ScReferenceExpression if isDirectContextRef(ref) =>
        val maybeResolved = ref.bind()
          .map(_.getActualElement)
          .map(nameContext)
          .collect(asValueOrVariable)

        val isSuspicious = maybeResolved.exists(resolved =>
          ref.parents.takeWhile(propagatesControlFlowToChildren).contains(resolved.getParent) &&
            ref.getTextOffset < resolved.getTextOffset
        )

        if (isSuspicious) {
          val description = ScalaBundle.message("suspicious.forward.reference.template.body")
          Some(manager.createProblemDescriptor(ref, description, isOnTheFly, Array.empty[LocalQuickFix], highlightType))
        } else None

      case _ => None
    }
  }
}

object ForwardReferenceInspection {
  private def isDirectContextRef(ref: ScReferenceExpression): Boolean =
    ref.smartQualifier.forall(isThisQualifier)

  private def isThisQualifier(expr: ScExpression): Boolean = expr match {
    case _: ScThisReference => true
    case _ => false
  }

  private def propagatesControlFlowToChildren(e: PsiElement): Boolean =
    !breaksControlFlowToChildren(e)

  private def breaksControlFlowToChildren(e: PsiElement): Boolean = e match {
    case v: ScValueOrVariable if v.hasModifierProperty("lazy") => true
    case _: ScClass | _: ScObject | _: ScFunction | _: ScFunctionExpr => true
    case e: ScBlockExpr if e.hasCaseClauses => true
    case _ => false
  }

  private def asValueOrVariable: PartialFunction[PsiElement, ScValueOrVariable] = {
    case v: ScValueOrVariable if !v.hasModifierProperty("lazy") => v
  }
}
