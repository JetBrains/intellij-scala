package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScQuotedPatternImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScPatternImpl
    with ScQuotedPattern {

  override def isIrrefutableFor(t: Option[ScType]): Boolean = false

  override def toString: String = "QuotedPattern"

  //TODO
  override def `type`(): TypeResult = Failure("Quoted pattern types are not supported yet")
}
