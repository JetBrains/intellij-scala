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
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor

/**
* @author ilyas, Alexander Podkhalyuzin
*/

class ScParenthesisedPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedPattern {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "PatternInParenthesis"

  override def getType(ctx: TypingContext) : TypeResult[ScType] = wrap(subpattern) flatMap {_.getType(ctx)}
}
