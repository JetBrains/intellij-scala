package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

object ArgumentSorting {

  def argumentPositionSortingKey(matchedParameter: (ScExpression, Parameter)): (Int, Int) = {
    val (expression, param) = matchedParameter
    // Actually supplied arguments have to be evaluated before default parameters
    val notDefault = expression.parent.exists(!_.is[ScParameter])
    if (notDefault) (0, expression.getTextOffset)
    else (1, param.index)
  }
}
