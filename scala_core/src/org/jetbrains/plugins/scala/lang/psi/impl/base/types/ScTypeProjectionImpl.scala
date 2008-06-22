package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import api.base.types._
import psi.types._

import com.intellij.lang.ASTNode

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScTypeProjectionImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypeProjection{
  override def toString: String = "TypeProjection"

  def projectedName() = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => None
    case id => Some(id.getText)
  }

  override def getType() = projectedName match {
    case None => Nothing
    case Some(name) => new ScProjectionType(typeElement.getType, name)
  }
}