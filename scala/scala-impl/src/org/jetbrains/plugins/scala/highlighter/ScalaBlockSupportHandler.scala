package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, IterableOnceExt, Parent, PsiElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.ScMarkerOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

class ScalaBlockSupportHandler extends CodeBlockSupportHandler {
  override def getCodeBlockMarkerRanges(psiElement: PsiElement): util.List[TextRange] = psiElement match {
    case Parent(ScMarkerOwner(begin, Some(end))) if psiElement == begin =>
      util.Arrays.asList(begin.getTextRange, end.getTextRange)
    case Parent(end: ScEnd) if psiElement == end.marker  =>
      (end.beginMarker.toSeq :+ end.marker).map(_.getTextRange).asJava
    case _ =>
      util.Collections.EMPTY_LIST.asInstanceOf[util.List[TextRange]]
  }

  override def getCodeBlockRange(psiElement: PsiElement): TextRange =
    psiElement.parentsInFile.findByType[ScMarkerOwner]
      .flatMap(block => block.endMarker.map(end => TextRange.create(block.beginMarker.getTextOffset, end.getTextRange.getEndOffset)))
      .getOrElse(TextRange.EMPTY_RANGE)
}
