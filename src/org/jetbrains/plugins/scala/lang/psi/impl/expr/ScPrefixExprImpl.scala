package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import collection.Seq
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScPrefixExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPrefixExpr {
  override def toString: String = "PrefixExpression"

  def argumentExpressions: Seq[ScExpression] = Seq.empty

  def getInvokedExpr: ScExpression = operation
}