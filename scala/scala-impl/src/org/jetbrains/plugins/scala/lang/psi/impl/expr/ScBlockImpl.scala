package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock

/**
 * Represents e.g. a block in case clause: {{{
 * 1 match {
 *   case _ =>
 *     println(1) // first block expression
 *     println(2) // second block expression
 * }
 * }}}
 * (if case clause contains braces it's represented by ScBlockExpr inside ScBlock)
 */
class ScBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScBlock {
  override def toString: String = "BlockOfExpressions"

  override def isEnclosedByBraces: Boolean = false
}