package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScParenthesisedPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternImpl with ScParenthesisedPattern {
  override def isIrrefutableFor(t: Option[ScType]): Boolean = innerElement.exists(_.isIrrefutableFor(t))

  override def toString: String = "PatternInParenthesis"

  override def `type`(): TypeResult = this.flatMapType(innerElement)
}
