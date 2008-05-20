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
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {

  /*override def getAbstractType : AbstractType= {
    val children = getChildren
    // For explicit type specification
/*
    if (children.length >0 && children(0).isInstanceOf[ScSimpleTypeElementImpl]) {
      return children(0).asInstanceOf[ScSimpleTypeElementImpl].getAbstractType
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