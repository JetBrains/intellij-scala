package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern


/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScGenerator extends ScalaPsiElement {

  def pattern: ScPattern

  def guard: ScGuard

  def rvalue: ScExpression

  override def accept(visitor: ScalaElementVisitor) = visitor.visitGenerator(this)
}