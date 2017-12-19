package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.collection.Seq

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScPrefixExprImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScPrefixExpr {

  def argumentExpressions: Seq[ScExpression] = Seq.empty

  def getInvokedExpr: ScExpression = operation

  override def toString: String = "PrefixExpression"
}