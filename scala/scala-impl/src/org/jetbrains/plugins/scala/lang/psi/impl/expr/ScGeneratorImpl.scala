package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 07.03.2008
  */
class ScGeneratorImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScGenerator {
  def pattern: ScPattern = findChildByClass(classOf[ScPattern])

  def guard: ScGuard = findChildByClass(classOf[ScGuard])

  def rvalue: ScExpression = findChildByClass(classOf[ScExpression])

  override def toString: String = "Generator"
}