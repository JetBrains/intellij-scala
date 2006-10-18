package org.jetbrains.plugins.scala.lang.psi.impl.literals {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScIdentifier( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Identifier: "+ getText
  }

  

}