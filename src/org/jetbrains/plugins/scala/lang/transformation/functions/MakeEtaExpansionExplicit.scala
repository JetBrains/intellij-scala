package org.jetbrains.plugins.scala.lang.transformation
package functions

import org.jetbrains.plugins.scala.extensions.{&&, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType

/**
  * @author Pavel Fatin
  */
object MakeEtaExpansionExplicit extends AbstractTransformer {
  def transformation = {
    case (e: ScReferenceExpression) && ReferenceTarget(m: ScFunction) &&
      NonValueType(t: ScMethodType) && ExpectedType(et: ScParameterizedType)
      if !e.getParent.isInstanceOf[ScUnderscoreSection] =>

      e.replace(code"$e _")

    case (e @ ScMethodCall(ReferenceTarget(m: ScFunction), _)) &&
      NonValueType(t: ScMethodType) && ExpectedType(et: ScParameterizedType)
      if !e.getParent.isInstanceOf[ScUnderscoreSection] =>

      e.replace(code"$e _")
  }
}
