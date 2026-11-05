package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd

import java.util

class ScalaBlockSupportHandler extends CodeBlockSupportHandler {
  override def getCodeBlockMarkerRanges(psiElement: PsiElement): util.List[TextRange] = psiElement match {
    case Parent(ScBegin(begin, Some(end))) if psiElement == begin.keyword =>
      util.Arrays.asList(begin.keyword.getTextRange, end.keyword.getTextRange)
    case Parent(ScEnd(Some(begin), end)) if psiElement == end.keyword  =>
      util.Arrays.asList(begin.keyword.getTextRange, end.keyword.getTextRange)
    case _ =>
      util.Collections.EMPTY_LIST.asInstanceOf[util.List[TextRange]]
  }

  override def getCodeBlockRange(psiElement: PsiElement): TextRange =
    psiElement.parentsInFile.findByType[ScBegin]
      .flatMap(begin => begin.end.map(end => TextRange.create(begin.keyword.startOffset, end.keyword.endOffset)))
      .getOrElse(TextRange.EMPTY_RANGE)
}
