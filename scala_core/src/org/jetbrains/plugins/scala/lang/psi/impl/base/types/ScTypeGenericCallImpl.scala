package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types._


/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScParameterizedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParameterizedTypeElement{

  override def toString: String = "ParametrizedTypeElement"

  def typeArgList = findChildByClass(classOf[ScTypeArgs])

  def typeElement = findChildByClass(classOf[ScTypeElement])

  override def getType() = {
    typeElement.getType match {
      case Nothing => Nothing
      case des => new ScParameterizedType(des, typeArgList.typeArgs.map {_.getType}.toArray)
    }
  }
}