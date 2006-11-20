package org.jetbrains.plugins.scala.lang.psi.impl.expressions{
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

  case class ScIfStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "IF statement"
  }
  case class ScForStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "FOR statement"
  }
  case class ScDoStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "DO statement"
  }
  case class ScTryStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "TRY statement"
  }
      case class ScTryBlockImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
          override def toString: String = "Try block"
      }
      case class ScCatchBlockImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
          override def toString: String = "Catch block"
      }
      case class ScFinallyBlockImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
          override def toString: String = "Finally block"
      }
  
  case class ScWhileStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "WHILE statement"
  }
  case class ScClosureImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Method closure"
  }
  case class ScReturnStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "RETURN statement"
  }
  case class ScThrowStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "THROW statement"
  }
  case class ScMatchStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "MATCH statement"
  }
  case class ScTypedStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Typed statement"
  }
  case class ScAssignStmtImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Assign statement"
  }


}

