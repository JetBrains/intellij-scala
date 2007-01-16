package org.jetbrains.plugins.scala.lang.psi.impl.expressions
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.source.ChildRole
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes


abstract class ScPsiExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node)                                           

/*class ScBracesExprImpl (node:ASTNode) extends ScPsiExprImpl(node) {
  def isExpr = (elementType : IElementType) => (elementType == ScalaElementTypes.EXPR)

  def getExpression = childSatisfyPredicate(isExpr)
}*/
/*
class ScalaParenthesizedExpressionImpl extends ScalaExpression {

//  @Nullable
  override def getExpression() : PsiExpression = findChildByRoleAsPsiElement(ChildRole.EXPRESSION).asInstanceOf[PsiExpression];

//  @Nullable
  override def getType() : PsiType = {
    val expr : PsiExpression = getExpression()
    if (expr == null) null else expr.getType();
  }

  override def getChildRole(child : ASTNode ) : Int = {
//    LOG.assertTrue(child.getTreeParent() == this);
    var i : IElementType = child.getElementType();

    i match {
      case ScalaTokenTypes.tLPARENTHIS => ChildRole.LPARENTH
      case ScalaTokenTypes.tRPARENTHIS => ChildRole.RPARENTH
      case _ if (EXPRESSION_BIT_SET.contains(i)) => ChildRole.EXPRESSION;
      case _ => ChildRole.NONE
    }
  }

  override def findChildByRole(role : Int) : ASTNode = {
//    LOG.assertTrue(ChildRole.isUnique(role));

    role match {
      case ChildRole.LPARENTH => TreeUtil.findChild(this, ScalaTokenTypes.tLPARENTHIS);
      case ChildRole.RPARENTH => TreeUtil.findChild(this, ScalaTokenTypes.tRPARENTHIS);
      case ChildRole.EXPRESSION => TreeUtil.findChild(this, ScalaElementTypes.EXPRESSION_BIT_SET);
      case _ => null
    }
  }

  override def toString() : String = "PsiParenthesizedExpression:" + getText();

  override def accept(visitor : PsiElementVisitor ) = {
    visitor.visitParenthesizedExpression(this);
  }
  */
