package org.jetbrains.plugins.scala.lang.psi.impl.patterns {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScPattern1Impl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Common pattern"
  }

  case class ScPatternImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Composite pattern"
  }

}
