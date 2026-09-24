package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScDoImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScDo {

  override def body: Option[ScExpression] = findChild[ScExpression]

  override def condition: Option[ScExpression] = findLastChild[ScExpression].filterNot(body.contains)

  override def toString: String = "DoStatement"
}