package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTryBlock

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScTryBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTryBlock {
  override def toString: String = "TryBlock"
}