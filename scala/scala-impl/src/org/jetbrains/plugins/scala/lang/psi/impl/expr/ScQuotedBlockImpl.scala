package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScQuotedBlock

class ScQuotedBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScQuotedBlock {
  override def toString: String = "QuotedBlock"
}