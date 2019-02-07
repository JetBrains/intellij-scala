package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.annotator.ScConstrBlockAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlock

final class ScConstrBlockImpl(node: ASTNode) extends ScExpressionImplBase(node)
  with ScConstrBlock with ScConstrBlockAnnotator {

  override def toString: String = "ConstructorBlock"
}