package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScConstrBlockExpr, ScSelfInvocation}

final class ScConstrBlockExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrBlockExpr {

  override def toString: String = "ConstructorBlock" // TODO: rename to ConstructorBlockExpression
}