package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

trait ScSimpleType extends ScalaType

/**
*   The most simple type
*/
class ScSimpleTypeImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleType {

  override def getClassType = {
    val children = getChildren
    // For explicit type specification
    if (children.length == 1 && children(0).isInstanceOf[ScStableId]){
      children(0).asInstanceOf[ScStableId].getReference.resolve
    } else {
      // TODO implement other cases
      null
    }

  }

  override def toString: String = "Simple Type"
}