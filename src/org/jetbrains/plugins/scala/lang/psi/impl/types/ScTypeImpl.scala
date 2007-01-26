package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

class ScTypeImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Function type"
}

class ScCompoundTypeImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Simple type"
}

class ScRefineStatImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Refinement statement"
}

class ScRefinementsImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Refinements"
}