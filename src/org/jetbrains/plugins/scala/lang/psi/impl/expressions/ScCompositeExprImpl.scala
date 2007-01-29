package org.jetbrains.plugins.scala.lang.psi.impl.expressions{
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.patterns.ScCaseClauseImpl
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.tree.TokenSet

  case class ScIfStmtImpl( node : ASTNode ) extends ScExprImpl(node) with IfElseIndent {
      override def toString: String = "IF statement"

      def isCondition = (e : PsiElement) => e.isInstanceOf[ScExpr1Impl]
      def condition : ScExpr1Impl = childSatisfyPredicateForPsiElement(isCondition).asInstanceOf[ScExpr1Impl]

      def getType() : PsiType = null
  }

  case class ScForStmtImpl( node : ASTNode ) extends ScExpr1Impl(node)  with IfElseIndent{
      override def toString: String = "FOR statement"

      def isEnumerators = (e : PsiElement) => e.isInstanceOf[ScEnumeratorsImpl]

      def enumerators : ScEnumeratorsImpl = childSatisfyPredicateForPsiElement(isEnumerators).asInstanceOf[ScEnumeratorsImpl]

      def getType() : PsiType = null
  }

  case class ScDoStmtImpl( node : ASTNode ) extends ScExpr1Impl(node)  with IfElseIndent{
      override def toString: String = "DO statement"

      def isCondition = (e : PsiElement) => e.isInstanceOf[ScExprImpl]
      def condition : ScExprImpl = /*childSatisfyPredicateForPsiElement(isCondition).asInstanceOf[ScExpr1Impl]*/
          childSatisfyPredicateForPsiElement(isCondition, getLastChild, (e : PsiElement) => e.getPrevSibling).asInstanceOf[ScExprImpl]

      def getType() : PsiType = null
  }

  case class ScTryStmtImpl( node : ASTNode ) extends ScExpr1Impl(node) {
      override def toString: String = "TRY statement"

      def isTryBlock = (e : IElementType) => ScalaElementTypes.TRY_BLOCK.equals(e)
      def tryBlock = childSatisfyPredicateForElementType(isTryBlock).asInstanceOf[ScTryBlockImpl]

      def isCatchBlock = (e : IElementType) => ScalaElementTypes.CATCH_BLOCK.equals(e)
      def catchBlock = childSatisfyPredicateForElementType(isCatchBlock).asInstanceOf[ScCatchBlockImpl]

      def getType() : PsiType = null
  }

      case class ScTryBlockImpl( node : ASTNode ) extends ScExpr1Impl(node) with BlockedIndent{
          override def toString: String = "Try block"
          def getType() : PsiType = null
      }
      case class ScCatchBlockImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with BlockedIndent{
          override def toString: String = "Catch block"

          def caseClauses : Iterable[ScCaseClauseImpl] = {
            val caseClauses = getChild(ScalaElementTypes.CASE_CLAUSES).asInstanceOf[ScalaPsiElementImpl]
            caseClauses.childrenOfType[ScCaseClauseImpl](TokenSet.create(Array(ScalaElementTypes.CASE_CLAUSE)))
          }

          def getType() : PsiType = null
      }
      case class ScFinallyBlockImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with IfElseIndent{
          override def toString: String = "Finally block"
          def getType() : PsiType = null
      }
  
  case class ScWhileStmtImpl( node : ASTNode ) extends ScExpr1Impl(node) with IfElseIndent {
      override def toString: String = "WHILE statement"

      def isCondition = (e : PsiElement) => e.isInstanceOf[ScExprImpl]

      def condition : ScExprImpl = childSatisfyPredicateForPsiElement(isCondition).asInstanceOf[ScExprImpl]

      def getType() : PsiType = null
  }

  case class ScClosureImpl( node : ASTNode ) extends ScExpr1Impl(node) {
      override def toString: String = "Method closure"
      def getType() : PsiType = null
  }

  case class ScReturnStmtImpl( node : ASTNode ) extends ScExpr1Impl(node) {
      override def toString: String = "RETURN statement"
      def getType() : PsiType = null
  }

  case class ScThrowStmtImpl( node : ASTNode ) extends ScExpr1Impl(node) {
      override def toString: String = "THROW statement"
      def getType() : PsiType = null
  }

  case class ScMatchStmtImpl( node : ASTNode ) extends ScExpr1Impl(node) {
      override def toString: String = "MATCH statement"
      def getType() : PsiType = null
  }

  case class ScTypedStmtImpl( node : ASTNode ) extends ScExpr1Impl(node) {
      override def toString: String = "Typed statement"
      def getType() : PsiType = null
  }

  case class ScAssignStmtImpl( node : ASTNode ) extends ScExpr1Impl(node) with IfElseIndent{
      override def toString: String = "Assign statement"
      def getType() : PsiType = null
  }
  
  case class ScCommonExprImpl( node : ASTNode ) extends ScExpr1Impl(node) {
    override def toString: String = "Common expression"
    def getType() : PsiType = null
  }
}