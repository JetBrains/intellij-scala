package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi._

trait ScType extends ScTypes

trait ScType1 extends ScType

class ScTypeImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with ScType {
      override def toString: String = "Functional type"
}

class ScCompoundTypeImpl( node : ASTNode ) extends ScTypeImpl(node) with ScSimpleType {
      override def toString: String = "Simple type"
}

class ScInfixTypeImpl( node : ASTNode ) extends ScTypeImpl(node) {
      override def toString: String = "Infix type"
}

class ScRefineStatImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Refinement statement"
}

class ScRefinementsImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Refinements"
}