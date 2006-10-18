package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

class ScSimpleTypeImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Type: " + getText
}