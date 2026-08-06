package org.jetbrains.plugins.scala.lang.surroundWith.descriptors

import com.intellij.lang.surroundWith.{SurroundDescriptor, Surrounder}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaIgnoreErrorHighlightingSurroundDescriptor.Surrounders
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.errorHighlighting.IgnoreErrorHighlightingSurrounder

final class ScalaIgnoreErrorHighlightingSurroundDescriptor extends SurroundDescriptor {
  override def getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array[PsiElement] = file match {
    case file: ScalaFile =>
      val startElement = file.findElementAt(startOffset)
      val endElement = file.findElementAt(endOffset - 1)
      if (startElement == null || endElement == null) return PsiElement.EMPTY_ARRAY
      val range = ScalaPsiUtil.getElementsRange(startElement, endElement)

      if (range.isEmpty || range.head.startOffset != startOffset || range.last.endOffset != endOffset)
        PsiElement.EMPTY_ARRAY
      else range.toArray
    case _ => PsiElement.EMPTY_ARRAY
  }

  override def getSurrounders: Array[Surrounder] = Surrounders

  override def isExclusive: Boolean = false
}

object ScalaIgnoreErrorHighlightingSurroundDescriptor {
  // Create the surrounders once (SCL-20817)
  private lazy val Surrounders = Array[Surrounder](
    new IgnoreErrorHighlightingSurrounder,
  )
}
