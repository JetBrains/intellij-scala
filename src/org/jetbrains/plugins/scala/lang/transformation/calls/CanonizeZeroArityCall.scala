package org.jetbrains.plugins.scala.lang.transformation
package calls

import org.jetbrains.plugins.scala.extensions.{&&, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object CanonizeZeroArityCall extends AbstractTransformer {
  def transformation = {
    case (e: ScReferenceExpression) && ReferenceTarget(f: ScFunctionDefinition)
      if f.hasParameterClause && !e.getParent.isInstanceOf[ScMethodCall] =>

      e.replace(code"$e()")
  }
}
