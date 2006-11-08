package org.jetbrains.plugins.scala.lang.psi.impl.expressions{
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScIfStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "If statement"
  }
  case class ScWhileStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "While statement"
  }
  case class ScReturnStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Return statement"
  }
  case class ScThrowStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Throw statement"
  }
  case class ScMatchStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Match statement"
  }
  case class ScTypedStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Typed statement"
  }
  case class ScAssignStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Assign statement"
  }


}

