package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock

/**
* @author ilyas
*/
class ScBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScBlock {
  override def toString: String = "BlockOfExpressions"

  override def isBraceless: Boolean = true
}