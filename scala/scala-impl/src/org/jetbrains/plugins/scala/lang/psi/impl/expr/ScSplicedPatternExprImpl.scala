package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSplicedPatternExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScSplicedPatternExprImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScSplicedPatternExpr {

  override def toString: String = "SplicedPatternExpression"

  //TODO
  override def `type`(): TypeResult = Failure("Spliced pattern expression types are not supported yet")
}