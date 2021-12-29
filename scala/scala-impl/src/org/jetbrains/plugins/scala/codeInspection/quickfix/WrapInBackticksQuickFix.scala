package org.jetbrains.plugins.scala.codeInspection.quickfix

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.quickfix.WrapInBackticksQuickFix.message
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectContext

abstract class WrapInBackticksQuickFix[T <: ScReference](reference: T, referenceBuilder: (String, ProjectContext) => T)
  extends AbstractFixOnPsiElement(message, reference) {
  override protected def doApplyFix(reference: T)(implicit project: Project): Unit =
    reference.replace(referenceBuilder(s"`${reference.refName}`", project))
}

object WrapInBackticksQuickFix {
  val message: String = ScalaInspectionBundle.message("wrap.in.backticks")
}

final class WrapRefExprInBackticksQuickFix(ref: ScReferenceExpression)
  extends WrapInBackticksQuickFix(ref, ScalaPsiElementFactory.createReferenceExpressionFromText(_)(_))

final class WrapStableCodeRefInBackticksQuickFix(ref: ScStableCodeReference)
  extends WrapInBackticksQuickFix(ref, ScalaPsiElementFactory.createReferenceFromText(_)(_))
