package org.jetbrains.plugins.scala.lang.psi.impl.expressions
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

  class ScInfixExprImpl( node : ASTNode ) extends ScPostfixExprImpl(node) {
      override def toString: String = "Infix expression"
//      override def getType() : PsiType = null
  }

  class ScSimpleExprImpl( node : ASTNode ) extends ScPrefixExprImpl(node) {
      override def toString: String = "Simple expression"
//      override def getType() : PsiType = null
  }

  class ScPrefixExprImpl( node : ASTNode ) extends ScInfixExprImpl(node) {
      override def toString: String = "Prefix expression"
//      override def getType() : PsiType = null
  }

  class ScPostfixExprImpl( node : ASTNode ) extends ScExpr1Impl(node) {
      override def toString: String = "Postfix expression"
//      def getType() : PsiType = null
  }
  
