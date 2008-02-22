package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.typechecker.types._

trait ScSimpleType2 extends ScalaType

/**
*   The most simple type
*/
class ScSimpleTypeImpl2(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleType2 {

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

  override def toString: String = "Simple Type"
}