package org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs

/** 
* @author ilyas
*/

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.typechecker._

import org.jetbrains.plugins.scala.lang.psi._

case class ScLiteralImpl(node: ASTNode) extends ScalaExpression (node){
  override def toString: String = "Literal"
}

case class ScUnitImpl(node: ASTNode) extends ScalaExpression (node) with BlockedIndent{

  override def toString: String = "unit"
}

/**
*  (Expr)
*/
case class ScParenthesisedExpr(node: ASTNode) extends ScalaExpression (node) with BlockedIndent{

  override def getAbstractType = {
    val child = childSatisfyPredicateForPsiElement((el: PsiElement) => el.isInstanceOf[IScalaExpression])
    (new ScalaTypeChecker).getTypeByTerm(child)
  }

  override def getReference =  {
    val child = childSatisfyPredicateForPsiElement((el: PsiElement) => el.isInstanceOf[ScalaExpression])
    if (child != null) {
      child.getReference
    } else {
      null
    }
  }

  override def toString: String = "expression in parentheses"
}