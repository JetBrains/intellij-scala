package org.jetbrains.plugins.scala.lang.transformation
package calls

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPrefixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object ExpandUnaryCall extends AbstractTransformer {
  def transformation = {
    case e @ ScPrefixExpr(l @ RenamedReference(s, t), r) if t == "unary_" + s =>
      e.replace(code"$r.$t}")
  }
}
