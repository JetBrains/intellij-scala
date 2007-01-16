package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 * Time: 14:39:44
 */

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;

import org.jetbrains.plugins.scala.util.DebugPrint

import org.jetbrains.plugins.scala.util.DebugPrint

import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScPsiExprImpl

abstract class ScalaExpressionSurrounder extends Surrounder {
// val LOG : Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.surrounder.SurroundExpressionHandler")

  override def isApplicable(elements : Array[PsiElement]) : Boolean = {
//    LOG.assertTrue(elements.length == 1 && elements(0).isInstanceOf[ScPsiExprImpl]);
    val expr = elements(0).asInstanceOf[ScPsiExprImpl]
    Console.println("ScalaExpressionSurrounder: isApplicable" + expr);

    isApplicable(expr)

//    Console.println(expr.toString())
//    true
  }

  def isApplicable(expr : ScPsiExprImpl) : Boolean

  override def surroundElements(project : Project, editor : Editor, elements : Array[PsiElement]) : TextRange = {
    DebugPrint.println("element0 : " + elements(0).asInstanceOf[ScPsiExprImpl]);
    
    surroundExpression(project, editor, elements(0).asInstanceOf[ScPsiExprImpl])
  }

  def surroundExpression(project : Project, editor : Editor, expr : ScPsiExprImpl) : TextRange
}
