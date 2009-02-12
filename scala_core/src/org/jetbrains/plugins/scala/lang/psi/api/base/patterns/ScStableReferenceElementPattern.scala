package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import expr.ScReferenceExpression

/**
 * @author ilyas
 */

trait ScStableReferenceElementPattern extends ScPattern {

  def refElement = findChild(classOf[ScReferenceElement])

  def getReferenceExpression = findChild(classOf[ScReferenceExpression])

}