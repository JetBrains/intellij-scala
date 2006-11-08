package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

class ScTypesImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Types list: "
}