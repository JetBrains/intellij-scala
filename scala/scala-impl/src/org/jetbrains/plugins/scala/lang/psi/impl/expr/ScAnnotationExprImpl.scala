package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationExpr

class ScAnnotationExprImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScAnnotationExpr{
  override def toString: String = "AnnotationExpression"
}