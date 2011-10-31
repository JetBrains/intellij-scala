package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.ScalaElementVisitor
import psi.types.result.{Success, Failure, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScInfixPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScInfixPattern {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "InfixPattern"

  override def getType(ctx: TypingContext) = {
    expectedType match {
      case Some(x) => Success(x, Some(this))
      case _ => Failure("cannot define expected type", Some(this))
    }
  }
}