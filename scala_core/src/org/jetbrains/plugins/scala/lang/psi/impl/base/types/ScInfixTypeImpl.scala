package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import psi.ScalaPsiElementImpl
import api.base.types._
import psi.types._

import com.intellij.lang.ASTNode

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScInfixTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixTypeElement{
  override def toString: String = "InfixType"

  def rOp = findChildrenByClass(classOf[ScTypeElement]) match {
    case Array(_, r) => Some(r)
    case _ => None
  }

  override def getType = rOp match {
    case None => Nothing
    case Some(rOp) => ref.bind match {
      case None => Nothing
      case Some(result) => new ScParameterizedType(new ScDesignatorType(result.element), Array(lOp.getType, rOp.getType))
    }
  }
}