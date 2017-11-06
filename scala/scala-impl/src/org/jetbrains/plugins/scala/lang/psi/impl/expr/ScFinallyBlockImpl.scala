package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFinallyBlock

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScFinallyBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScFinallyBlock {
  override def toString: String = "FinallyBlock"
}