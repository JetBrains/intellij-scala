package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScTuple extends ScExpression {
  def exprs : Seq[ScExpression] = Seq(findChildrenByClass(classOf[ScExpression]): _*)
}