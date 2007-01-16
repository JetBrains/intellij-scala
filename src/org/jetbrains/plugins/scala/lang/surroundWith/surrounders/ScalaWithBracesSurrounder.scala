  package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @author: Dmitry Krasilschikov
 */

import org.jetbrains.plugins.scala.util.DebugPrint

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScBlockExprImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScPsiExprImpl


class ScalaWithBracesSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(expr : ScPsiExprImpl) : Boolean = {
    Console.println("ScalaWithBracesSurrounder : isAplicable");
    Console.println("ScalaWithBracesSurrounder : isAplicable, expr : " + expr);
    true
  }

  override def surroundExpression(project : Project, editor : Editor, expr : ScPsiExprImpl) : TextRange = {
    DebugPrint.println("expr : " + expr)
    val offset = expr.getTextRange().getEndOffset();

    var blockExpr : ScPsiExprImpl = Expr.createExpressionFromText("{{a}}")
    var braceExpr : ScBlockExprImpl = null;

    DebugPrint.println("parsed expr : " + blockExpr);

    if (blockExpr.isInstanceOf[ScBlockExprImpl]) braceExpr = blockExpr.asInstanceOf[ScBlockExprImpl]
    else /*return {new TextRange(offset, offset)}*/ throw new IncorrectOperationException("{{a}} is not a block expression; a is " + blockExpr)

//    val exprInside : ScPsiExprImpl = braceExpr.getExpression

    if (braceExpr.getExpression == null) /*{return new TextRange(offset, offset)}*/
    throw new IncorrectOperationException("there is no expression in parenthises")
    else braceExpr.getExpression.replace(expr)

    return new TextRange(offset, offset);
  }

  override def getTemplateDescription() : String = {{{
    "surround with braces template"
  }}}
}
