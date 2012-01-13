package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
 * Author: Alexander Podkhalyuzin
 * Date: 06.03.2008
 */
trait ScCatchBlock extends ScalaPsiElement {
  def expression: Option[ScExpression] = findChild(classOf[ScExpression])
}