package org.jetbrains.plugins.scala.lang.transformation.annotations

import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.transformation._

/**
  * @author Pavel Fatin
  */
object AddTypeToFunctionParameter extends AbstractTransformer {
  def transformation = {
    case (p: ScParameter) && Parent(e @ Parent(Parent(_: ScFunctionExpr))) if p.paramType.isEmpty =>

      val annotation = annotationFor(p.getRealParameterType().get, e)

      val f = code"(${p.text}: ${annotation.text}) => ()"

      e.replace(f.getFirstChild.getChildren.apply(0))
  }
}
