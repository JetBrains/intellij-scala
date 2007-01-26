package org.jetbrains.plugins.scala.lang.surroundWith.descriptors;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 * Time: 16:36:32
 */
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.debugger.codeinsight.JavaWithRuntimeCastSurrounder;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.PsiWhiteSpace;

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScExprImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.ScInfixExprImpl
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders._

           
class ScalaExpressionSurroundDescriptor extends SurroundDescriptor {
  val BRACES_SURROUNDER = 0
  val PARENTHESES_SURROUDNER = 1
  val IF_SURROUNDER = 2
  val FOR_SURROUNDER = 3
  val WHILE_SURROUNDER = 4
  val DO_WHILE_SURROUNDER = 5
  val TRY_SURROUNDER = 6
  val TRY_CATCH_SURROUNDER = 7

  private val SURROUNDERS : Array[Surrounder] = {
    val surrounders = new Array[Surrounder](8)
    surrounders(BRACES_SURROUNDER) = new ScalaWithBracketsSurrounder("{", "}")
    surrounders(PARENTHESES_SURROUDNER) = new ScalaWithBracketsSurrounder("(", ")")
    surrounders(IF_SURROUNDER) = new ScalaWithIfSurrounder()
    surrounders(FOR_SURROUNDER) = new ScalaWithForSurrounder()
    surrounders(WHILE_SURROUNDER) = new ScalaWithWhileSurrounder()
    surrounders(DO_WHILE_SURROUNDER) = new ScalaWithDoWhileSurrounder()
    surrounders(TRY_SURROUNDER) = new ScalaWithTrySurrounder()
    surrounders(TRY_CATCH_SURROUNDER) = new ScalaWithTryCatchSurrounder()
    surrounders

    /*BRACES_SURROUNDER,
    PARENTHIS_SURROUDNER,
    IF_SURROUNDER,
    FOR_SURROUNDER,
    WHILE_SURROUNDER,
    DO_WHILE_SURROUNDER,
    TRY_SURROUNDER,
    TRY_CATCH_SURROUNDER*/
  }

  override def getSurrounders() : Array[Surrounder] = SURROUNDERS

  override def getElementsToSurround(file : PsiFile, startOffset : Int, endOffset : Int) : Array[PsiElement] = {
    val expr : ScExprImpl = findExpressionInRange(file, startOffset, endOffset)
    if (expr == null) return PsiElement.EMPTY_ARRAY

    Array.apply(expr)
  }

  def findExpressionInRange(file : PsiFile, startOffset : Int, endOffset : Int) : ScExprImpl = {

    val element1 : PsiElement = file.findElementAt(startOffset);
    val element2 : PsiElement = file.findElementAt(endOffset - 1);

    var endOffsetLocal : Int = endOffset
    var startOffsetLocal : Int = startOffset

    if (element1.isInstanceOf[PsiWhiteSpace]) {
      startOffsetLocal = element1.getTextRange().getEndOffset();
    }

    if (element2.isInstanceOf[PsiWhiteSpace]) {
      endOffsetLocal = element2.getTextRange().getStartOffset();
    }

    val expression : ScExprImpl = PsiTreeUtil.findElementOfClassAtRange[ScExprImpl](file, startOffsetLocal, endOffsetLocal, classOf[ScExprImpl].asInstanceOf[java.lang.Class[ScExprImpl]]);

    if (expression == null || expression.getTextRange().getEndOffset() != endOffsetLocal) return null
    
    return expression;
  }
}