package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import psi.ScalaPsiElementImpl
import api.base.types._
import psi.types._
import com.intellij.lang.ASTNode
import result.{TypeResult, TypingContext}

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScInfixTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixTypeElement {
  override def toString: String = "InfixType"

  def rOp = findChildrenByClass(classOf[ScTypeElement]) match {
    case Array(_, r) => Some(r)
    case _ => None
  }

  def getType(ctx: TypingContext): TypeResult[ScType] = for {
    rop <- wrap(rOp)(ScalaBundle.message("no.right.operand.found"))
    element <- wrap(ref.bind.map(_.element))("cannot.resolve.infix.operator")
    rType <- rop.getType(ctx)
    lType <- lOp.getType(ctx)
  }
  yield new ScParameterizedType(new ScDesignatorType(element), Seq(lType, rType))

}