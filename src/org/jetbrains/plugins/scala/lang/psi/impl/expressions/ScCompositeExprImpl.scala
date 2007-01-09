package org.jetbrains.plugins.scala.lang.psi.impl.expressions{
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi._

  case class ScIfStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "IF statement"
      def getType() : PsiType = null
  }
  case class ScForStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "FOR statement"
      def getType() : PsiType = null
  }
  case class ScDoStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "DO statement"
      def getType() : PsiType = null
  }
  case class ScTryStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "TRY statement"
      def getType() : PsiType = null
  }
      case class ScTryBlockImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
          override def toString: String = "Try block"
          def getType() : PsiType = null
      }
      case class ScCatchBlockImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
          override def toString: String = "Catch block"
          def getType() : PsiType = null
      }
      case class ScFinallyBlockImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
          override def toString: String = "Finally block"
          def getType() : PsiType = null
      }
  
  case class ScWhileStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "WHILE statement"
      def getType() : PsiType = null
  }
  case class ScClosureImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "Method closure"
      def getType() : PsiType = null
  }
  case class ScReturnStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "RETURN statement"
      def getType() : PsiType = null
  }
  case class ScThrowStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "THROW statement"
      def getType() : PsiType = null
  }
  case class ScMatchStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "MATCH statement"
      def getType() : PsiType = null
  }
  case class ScTypedStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "Typed statement"
      def getType() : PsiType = null
  }
  case class ScAssignStmtImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
      override def toString: String = "Assign statement"
      def getType() : PsiType = null
  }
  
  case class ScExprImpl( node : ASTNode ) extends ScPsiExprImpl(node) {
    override def toString: String = "Common expression"
    def getType() : PsiType = null
  }


}

