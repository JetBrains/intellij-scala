package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlock

final class ScConstrBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrBlock {

  override def toString: String = "ConstructorBlock"

  override def isBraceless: Boolean = false
}