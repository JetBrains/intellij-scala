package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class CanonizeZeroArityCall extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScReferenceExpression) && ReferenceTarget(f: ScFunctionDefinition)
      if f.hasParameterClause && !e.getParent.isInstanceOf[ScMethodCall] =>

      e.replace(code"$e()")
  }
}
