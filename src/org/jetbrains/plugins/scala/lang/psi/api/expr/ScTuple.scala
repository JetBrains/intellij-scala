package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScTuple extends ScInfixArgumentExpression {
  def exprs : Seq[ScExpression] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScExpression]).toSeq: _*)

  /**
   * Return possible applications without using resolve of reference to this call (to avoid SOE)
   */
  def possibleApplications: Array[Array[(String, ScType)]]
}