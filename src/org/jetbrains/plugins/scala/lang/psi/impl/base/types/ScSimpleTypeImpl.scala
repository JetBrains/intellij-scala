package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl




import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 22.02.2008
* Time: 13:56:10
* To change this template use File | Settings | File Templates.
*/

class ScSimpleTypeImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleType {

  /*override def getAbstractType : AbstractType= {
    val children = getChildren
    // For explicit type specification
/*
    if (children.length >0 && children(0).isInstanceOf[ScSimpleTypeImpl]) {
      return children(0).asInstanceOf[ScSimpleTypeImpl].getAbstractType
    }
*/
    if (children.length == 1 && children(0).isInstanceOf[ScStableId] &&
    children(0).asInstanceOf[ScStableId].getReference != null){
      val classType = children(0).asInstanceOf[ScStableId].getReference.resolve
      if (classType.isInstanceOf[ScTypeDefinition]) {
        new ValueType(classType.asInstanceOf[ScTypeDefinition], null)
      } else {
        null
      }
    } else {
      // TODO implement other cases
      null
    }

  }*/

  override def toString: String = "TypeElement"
}