package org.jetbrains.plugins.scala.lang.psi.impl.literals {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScLiteralImpl( node : ASTNode ) extends ScalaPsiElementImpl ( node ){
      override def toString: String = "Literal: "+ getText
  }

  /**
  * Implementation of integer literals
  */
  case class ScIntegerImpl( node : ASTNode ) extends ScalaPsiElementImpl ( node ){
      override def toString: String = "Integer Literal: "+ getText
  }

  /**
  * Implementation of floating point number literals
  */
  case class ScFloatImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Real Literal: "+ getText
  }

  /**
  * Implementation for boolean literals
  */
  case class ScBooleanImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Boolean Literal: "+ getText
  }

  /**
  * Implementation of character literals
  */
  case class ScCharacterImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Character Literal: "+ getText
  }

  /**
  * Implementation of NULL literal
  */
  case class ScNullImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "null"
  }

  /**
  * Implementation of String literal
  */
  case class ScStringImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "String Literal: "+ getText
  }
  case class ScStringBeginImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
    override def toString: String = "String Begin - double quote "
  }
  case class ScStringContentImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
    override def toString: String = "String content " + getText
  }
  case class ScStringEndImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
    override def toString: String = "String End - double quote "
  }




}