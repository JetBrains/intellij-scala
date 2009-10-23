package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import psi.types.result.{TypeResult, TypingContext}
import psi.types.ScType

/**
* @author ilyas, Alexander Podkhalyuzin
*/

class ScParenthesisedPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedPattern {
  override def toString: String = "PatternInParenthesis"

  override def getType(ctx: TypingContext) : TypeResult[ScType] = wrap(subpattern) flatMap {_.getType(ctx)}
}
