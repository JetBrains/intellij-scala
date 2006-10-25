package org.jetbrains.plugins.scala.lang.psi.impl.expressions {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScInfixExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Infix expression "+ getText
  }

  case class ScPrefixExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Prefix expression "+ getText
  }
    case class ScPrefixImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Simple prefix "+ getText
    }

  case class ScPostfixExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Postfix expression "+ getText
  }

  case class ScAssignImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Operator: assign"
  }

}