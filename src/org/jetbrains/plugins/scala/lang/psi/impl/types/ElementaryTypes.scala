package org.jetbrains.plugins.scala.lang.psi.impl.literals {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScIdentifierImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Identifier: "+ getText
  }

  case class ScThisImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Keyword: this"
  }

  case class ScSuperImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Keyword: super"
  }

  case class ScDotImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Separator: dot"
  }

  case class ScLsqbracketImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Left square bracket"
  }

  case class ScRsqbracketImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Right square bracket"
  }

  case class ScTypeImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Keyword: type"
  }

  

}