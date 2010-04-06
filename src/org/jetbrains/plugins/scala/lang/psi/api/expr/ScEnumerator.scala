package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScEnumerator extends ScalaPsiElement {
  
  def pattern: ScPattern

  def rvalue: ScExpression

  override def accept(visitor: ScalaElementVisitor) = visitor.visitEnumerator(this)
}