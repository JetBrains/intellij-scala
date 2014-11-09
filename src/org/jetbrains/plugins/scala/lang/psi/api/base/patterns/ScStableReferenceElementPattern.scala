package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * @author ilyas
 */

trait ScStableReferenceElementPattern extends ScPattern {

  def refElement = findChild(classOf[ScReferenceElement])

  def getReferenceExpression = findChild(classOf[ScReferenceExpression])

}