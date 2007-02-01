package org.jetbrains.plugins.scala.lang.psi.impl.literals {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

import org.jetbrains.plugins.scala.lang.psi._

  case class ScLiteralImpl( node : ASTNode ) extends ScSimpleExprImpl ( node ){
      override def toString: String = "Literal"
      def getType() : PsiType = null
  }

  case class ScUnitImpl( node : ASTNode ) extends ScExprImpl ( node ) with BlockedIndent{
      override def toString: String = "unit"
      def getType() : PsiType = null
  }

/*
  /**
  * Implementation of integer literals
  */
  case class ScIntegerImpl( node : ASTNode ) extends ScLiteralImpl ( node ){
      override def toString: String = "Integer Literal: "+ getText
  }

  /**
  * Implementation of floating point number literals
  */
  case class ScFloatImpl( node : ASTNode ) extends ScLiteralImpl(node) {
      override def toString: String = "Real Literal: "+ getText
  }

  /**
  * Implementation for boolean literals
  */
  case class ScBooleanImpl( node : ASTNode ) extends ScLiteralImpl(node) {
      override def toString: String = "Boolean Literal: "+ getText
  }

  /**
  * Implementation of character literals
  */
  case class ScCharacterImpl( node : ASTNode ) extends ScLiteralImpl(node) {
      override def toString: String = "Character Literal: "+ getText
  }

  /**
  * Implementation of NULL literal
  */
  case class ScNullImpl( node : ASTNode ) extends ScLiteralImpl(node) {
      override def toString: String = "null"
      def getType() : PsiType = null
  }

  /**
  * Implementation of String literal
  */
  case class ScStringImpl( node : ASTNode ) extends ScLiteralImpl(node) {
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
*/



}