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

trait ScStableReferencePattern extends ScPattern {

  def refElement: Option[ScReference] = findChild(classOf[ScReference])

  def getReferenceExpression: Option[ScReferenceExpression] = findChild(classOf[ScReferenceExpression])

}