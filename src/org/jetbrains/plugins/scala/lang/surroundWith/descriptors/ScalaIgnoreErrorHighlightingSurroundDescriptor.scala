package org.jetbrains.plugins.scala.lang.surroundWith.descriptors

import com.intellij.lang.surroundWith.{SurroundDescriptor, Surrounder}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.errorHighlighting.IgnoreErrorHighlightingSurrounder


/**
 * @author Alefas
 * @since 10.04.12
 */

class ScalaIgnoreErrorHighlightingSurroundDescriptor extends SurroundDescriptor {
  def getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array[PsiElement] = {
    if (!file.isInstanceOf[ScalaFile]) return PsiElement.EMPTY_ARRAY
    val startElement = file.findElementAt(startOffset)
    val endElement = file.findElementAt(endOffset - 1)
    if (startElement == null || endElement == null) return PsiElement.EMPTY_ARRAY
    val range = ScalaPsiUtil.getElementsRange(startElement, endElement)
    if (range.length == 0) return PsiElement.EMPTY_ARRAY
    if (range(0).getTextRange.getStartOffset != startOffset) return PsiElement.EMPTY_ARRAY
    if (range(range.length - 1).getTextRange.getEndOffset != endOffset) return PsiElement.EMPTY_ARRAY
    range.toArray
  }

  def getSurrounders: Array[Surrounder] = Array(new IgnoreErrorHighlightingSurrounder)

  def isExclusive: Boolean = false
}
