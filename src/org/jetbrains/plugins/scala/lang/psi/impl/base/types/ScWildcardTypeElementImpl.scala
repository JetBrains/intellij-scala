package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import base.ScTypeBoundsOwnerImpl
import psi.types.result.TypingContext
import psi.types.ScExistentialArgument
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
* @author Alexander Podkhalyuzin
* Date: 11.04.2008
*/

class ScWildcardTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeBoundsOwnerImpl with ScWildcardTypeElement {
  override def toString: String = "WildcardType"

  protected def innerType(ctx: TypingContext) = for (
    lb <- lowerBound;
    ub <- upperBound
  ) yield new ScExistentialArgument("_", Nil, lb, ub)

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitWildcardTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitWildcardTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}