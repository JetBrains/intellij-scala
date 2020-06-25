package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSplicedBlock

class ScSplicedBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScSplicedBlock {
  override protected val typeName: String = "Spliced Block"
  override def toString: String = "SplicedBlock"
}