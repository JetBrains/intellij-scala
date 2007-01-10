package org.jetbrains.plugins.scala.lang.surrounder.descriptors;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 * Time: 16:36:32
 */
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiExpression;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.debugger.codeinsight.JavaWithRuntimeCastSurrounder;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.PsiWhiteSpace;

import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScExprImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScInfixExprImpl
import org.jetbrains.plugins.scala.lang.surrounder.ScalaWithParenthisSurrounder
           
class ScalaExpressionSurroundDescriptor extends SurroundDescriptor {
  private val SURROUNDERS : Array[Surrounder] = Array.apply (
    new ScalaWithParenthisSurrounder()
  )

  def getSurrounders()  : Array[Surrounder] = SURROUNDERS

  def getElementsToSurround(file : PsiFile, startOffset : Int, endOffset : Int) : Array[PsiElement] = {
    Console println "ScalaExpressionSurroundDescriptor: getElementsToSurround"
    //todo
    val expr : PsiExpression = findExpressionInRange(file, startOffset, endOffset);

    if (expr == null) return PsiElement.EMPTY_ARRAY;

    Console.println("expr: " + expr.toString())

//    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression");
    Array.apply(expr)
  }


  def findExpressionInRange(file : PsiFile, startOffset : Int, endOffset : Int) : ScExprImpl = {
    Console.println("findExpressionInRange: startOffset = " + startOffset)
    Console.println("findExpressionInRange: endOffset = " + endOffset)

    val element1 : PsiElement = file.findElementAt(startOffset);
    val element2 : PsiElement = file.findElementAt(endOffset - 1);

    Console println ("findExpressionInRange: element1= " + element1.toString())
    Console println ("findExpressionInRange: element2= " + element2.toString())

    var endOffsetLocal : Int = endOffset
    var startOffsetLocal : Int = startOffset

    if (element1.isInstanceOf[PsiWhiteSpace]) {
      startOffsetLocal = element1.getTextRange().getEndOffset();
    }

    if (element2.isInstanceOf[PsiWhiteSpace]) {
      endOffsetLocal = element2.getTextRange().getStartOffset();
    }

    val expression : ScExprImpl = PsiTreeUtil.findElementOfClassAtRange[ScExprImpl](file, startOffsetLocal, endOffsetLocal, classOf[ScExprImpl].asInstanceOf[java.lang.Class[ScExprImpl]]);
    if (expression == null || expression.getTextRange().getEndOffset() != endOffsetLocal) return null;
//    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof JSCallExpression) return null;
    return expression;
  }
}
