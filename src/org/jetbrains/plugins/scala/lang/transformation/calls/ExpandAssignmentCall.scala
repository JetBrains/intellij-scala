package org.jetbrains.plugins.scala.lang.transformation
package calls

import org.jetbrains.plugins.scala.extensions.{ElementName, ReferenceTarget}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object ExpandAssignmentCall extends AbstractTransformer {
  def transformation = {
    case e @ ScInfixExpr(l, o @ ReferenceTarget(ElementName(name)), r) if o.text == name + "=" =>
      val (a, b) = if (name.endsWith(":")) (r, l) else (l, r)
      e.replace(code"$l = $a $name $b")
  }
}
