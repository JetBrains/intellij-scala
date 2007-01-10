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
import com.intellij.psi.PsiExpression;


import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScExpression

abstract class ScalaExpressionSurrounder extends Surrounder {
 val LOG : Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.surrounder.SurroundExpressionHandler")

 override def isApplicable(elements : Array[PsiElement]) : Boolean = {
    LOG.assertTrue(elements.length == 1 && elements(0).isInstanceOf[PsiExpression]);
    isApplicable(elements(0).asInstanceOf[PsiExpression])
  }

  def isApplicable(expr : PsiExpression) : Boolean

  override def surroundElements(project : Project, editor : Editor, elements : Array[PsiElement]) : TextRange = {
    surroundExpression(project, editor, elements(0).asInstanceOf[PsiExpression])
  }

  def surroundExpression(project : Project, editor : Editor, expr : PsiExpression) : TextRange
}
