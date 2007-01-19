/*
package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

*/
/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 * Time: 14:51:47
 */
/*
import org.jetbrains.plugins.scala.util.DebugPrint

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.plugins.scala.util.DebugPrint;

class ScalaWithParenthisSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(expr : PsiExpression) : Boolean = true; Console.println("ScalaWithParenthisSurrounder : isAplicable");

  override def surroundExpression(project : Project, editor : Editor, expr : PsiExpression ) : TextRange =  {
  
    Console.println ("expression 'll be in parenthis : " + expr.toString())

//    val manager : PsiManager = expr.getManager()
//    val factory : PsiElementFactory = manager.getElementFactory()
//    val codeStyleManager : CodeStyleManager = CodeStyleManager.getInstance(project)


    var parenthExpr = ScalaPsiElementFactory.createCompositeElement(ScalaElementTypes.PARENTHESIZED_EXPR)

    parenthExpr = codeStyleManager.reformat(parenthExpr).asInstanceOf[PsiParenthesizedExpression]

    parenthExpr.getExpression().replace(expr);

    val localExpr = expr.replace(parenthExpr).asInstanceOf[PsiExpression]
    val offset = localExpr.getTextRange().getEndOffset();

    return new TextRange(offset, offset);

    */
/*var parenthExpr : PsiParenthesizedExpression = factory.createExpressionFromText("(a)", null).asInstanceOf[PsiParenthesizedExpression]
    Console println ("parenthesed expression : " + parenthExpr.getExpression())

    parenthExpr = codeStyleManager.reformat(parenthExpr).asInstanceOf[PsiParenthesizedExpression]

    DebugPrint println ("parenthesed expression after reformat: " + parenthExpr.getExpression())

    parenthExpr.getExpression().replace(expr);
    val localExpr = expr.replace(parenthExpr).asInstanceOf[PsiExpression]
    val offset = localExpr.getTextRange().getEndOffset();
    */
/*
//    val offset = 0
//
//    new TextRange(offset, offset);
  }

  */
/*def replace(newElement : PsiElement ) : PsiElement = {
//    LOG.assertTrue(getTreeParent() != null);
    CheckUtil.checkWritable(this);
    var elementCopy : TreeElement = ChangeUtil.copyToElement(newElement);
    getTreeParent().replaceChildInternal(this, elementCopy);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    final PsiElement result = SourceTreeToPsiMap.treeElementToPsi(elementCopy);

    TreeUtil.invalidate(this);
    return result;
  }*/
/*

  override def getTemplateDescription() : String = {
//    CodeInsightBundle.message("surround.with.parenthesis.template", null);
    "surround with parenthesis template"
  }
}
*/
