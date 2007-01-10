package org.jetbrains.plugins.scala.lang.surrounder;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 * Time: 14:51:47
 */

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.codeStyle.CodeStyleManager;

class ScalaWithParenthisSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(expr : PsiExpression) : Boolean = true; Console.println("ScalaWithParenthisSurrounder : isAplicable");

  override def surroundExpression(project : Project, editor : Editor, expr : PsiExpression ) : TextRange =  {
    Console println "expression in parenthis done"
    val manager : PsiManager = expr.getManager()
    val factory : PsiElementFactory = manager.getElementFactory()
    val codeStyleManager : CodeStyleManager = CodeStyleManager.getInstance(project)

    var parenthExpr = factory.createExpressionFromText("(a)", null).asInstanceOf[PsiParenthesizedExpression]
    parenthExpr = codeStyleManager.reformat(parenthExpr).asInstanceOf[PsiParenthesizedExpression]
    parenthExpr.getExpression().replace(expr);
    val localExpr = expr.replace(parenthExpr).asInstanceOf[PsiExpression]
    val offset = localExpr.getTextRange().getEndOffset();

    new TextRange(offset, offset);
  }

  override def getTemplateDescription() : String = {
    CodeInsightBundle.message("surround.with.parenthesis.template", null);
  }
}
