package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import api.base.patterns.ScStableReferenceElementPattern
import api.expr.{ScReferenceExpression, ScExpression}
import com.intellij.lang.ASTNode
import psi.types.result.TypingContext
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor

/**
 * @author ilyas
 */

class ScStableReferenceElementPatternImpl(node : ASTNode) extends ScalaPsiElementImpl(node) with ScStableReferenceElementPattern {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "StableElementPattern"

  override def getType(ctx:TypingContext) = wrap(getReferenceExpression) flatMap {e => e.getType(TypingContext.empty)}
}