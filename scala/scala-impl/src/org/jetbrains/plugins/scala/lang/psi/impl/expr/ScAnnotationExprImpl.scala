package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScAnnotationExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScAnnotationExpr{
  override def toString: String = "AnnotationExpression"
}