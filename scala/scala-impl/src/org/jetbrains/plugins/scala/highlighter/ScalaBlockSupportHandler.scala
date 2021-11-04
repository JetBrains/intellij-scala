package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, IterableOnceExt, Parent, PsiElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

class ScalaBlockSupportHandler extends CodeBlockSupportHandler {
  override def getCodeBlockMarkerRanges(psiElement: PsiElement): util.List[TextRange] = psiElement match {
    case Parent(ScBegin(begin, Some(end))) if psiElement == begin =>
      util.Arrays.asList(begin.getTextRange, end.getTextRange)
    case Parent(end: ScEnd) if psiElement == end.marker  =>
      (end.begin.map(_.marker).toSeq :+ end.marker).map(_.getTextRange).asJava
    case _ =>
      util.Collections.EMPTY_LIST.asInstanceOf[util.List[TextRange]]
  }

  override def getCodeBlockRange(psiElement: PsiElement): TextRange =
    psiElement.parentsInFile.findByType[ScBegin]
      .flatMap(begin => begin.end.map(end => TextRange.create(begin.marker.getTextOffset, end.marker.getTextRange.getEndOffset)))
      .getOrElse(TextRange.EMPTY_RANGE)
}
