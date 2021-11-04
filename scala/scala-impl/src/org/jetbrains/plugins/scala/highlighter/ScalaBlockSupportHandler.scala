package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, IterableOnceExt, Parent, PsiElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.ScMarkerOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd

import java.util

class ScalaBlockSupportHandler extends CodeBlockSupportHandler {
  override def getCodeBlockMarkerRanges(psiElement: PsiElement): util.List[TextRange] = psiElement match {
    case Parent(ScMarkerOwner(begin, Some(end))) if psiElement == begin =>
      util.Arrays.asList(begin.getTextRange, end.getTextRange)
    case Parent((end: ScEnd) && ResolvesTo(block: ScMarkerOwner)) if psiElement == end.getFirstChild =>
      util.Arrays.asList(block.beginMarker.getTextRange, end.getFirstChild.getTextRange)
    case _ =>
      util.Collections.EMPTY_LIST.asInstanceOf[util.List[TextRange]]
  }

  override def getCodeBlockRange(psiElement: PsiElement): TextRange =
    psiElement.parentsInFile.findByType[ScMarkerOwner]
      .flatMap(block => block.endMarker.map(end => TextRange.create(block.beginMarker.getTextOffset, end.getTextRange.getEndOffset)))
      .getOrElse(TextRange.EMPTY_RANGE)
}
