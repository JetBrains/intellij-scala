package org.jetbrains.plugins.scala.lang.transformation
package general

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTuple
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object ExpandTupleInstantiation extends AbstractTransformer {
  def transformation = {
    case (e @ ScTuple(exprs)) =>
      e.replace(code"Tuple${exprs.length}(${@@(exprs)})")
  }
}
