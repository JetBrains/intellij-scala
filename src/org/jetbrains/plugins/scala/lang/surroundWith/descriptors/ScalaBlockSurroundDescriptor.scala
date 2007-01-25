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
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders._


class ScalaBlockSurroundDescriptor extends SurroundDescriptor {
  val TRY_SURROUNDER = 0
  val TRY_CATCH_SURROUNDER = 1

  private val SURROUNDERS : Array[Surrounder] = {
    val surrounders = new Array[Surrounder](2)
    surrounders(TRY_SURROUNDER) = new ScalaWithTrySurrounder()
    surrounders(TRY_CATCH_SURROUNDER) = new ScalaWithTryCatchSurrounder()
    surrounders
  }

  override def getSurrounders() : Array[Surrounder] = SURROUNDERS

  override def getElementsToSurround(file : PsiFile, startOffset : Int, endOffset : Int) : Array[PsiElement] = {
    val block : ScBlock = findBlockInRange(file, startOffset, endOffset)
    if (block == null) return PsiElement.EMPTY_ARRAY

    Array.apply(block)
  }

  def findBlockInRange(file : PsiFile, startOffset : Int, endOffset : Int) : ScBlock = {

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

    val block : ScBlock = PsiTreeUtil.findElementOfClassAtRange[ScBlock](file, startOffsetLocal, endOffsetLocal, classOf[ScBlock].asInstanceOf[java.lang.Class[ScBlock]]);

    if (block == null || block.getTextRange().getEndOffset() != endOffsetLocal) return null

    return block;
  }
}
