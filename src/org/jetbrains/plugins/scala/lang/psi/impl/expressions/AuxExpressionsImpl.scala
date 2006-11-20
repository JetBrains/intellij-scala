package org.jetbrains.plugins.scala.lang.psi.impl.expressions {
/**
* @author Ilya Sergey
* PSI implementation for auxiliary expressions
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScArgumentExprsImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Argument expressions"
  }

  case class ScBlockExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Block expressions"
  }

  case class ScBlockStatImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Common block statement"
  }

  case class ScBindingImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Binding"
  }

  case class ScEnumeratorImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Enumerator"
  }

  case class ScEnumeratorsImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Enumerators"
  }

  case class ScAnFunImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Anonymous function"
  }

}