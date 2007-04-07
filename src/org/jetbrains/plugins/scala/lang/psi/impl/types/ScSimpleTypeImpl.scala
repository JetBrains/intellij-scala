package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

trait ScSimpleType extends ScType1

/**
*   The most simple type
*/
class ScSimpleTypeImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with ScSimpleType {
      override def toString: String = "Simple Type"
}