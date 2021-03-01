package org.jetbrains.plugins.scala.highlighter.usages

import java.util
import java.util.Collections

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

private class CompanionHighlightHandler(keyword: PsiElement, definition: ScTypeDefinition, editor: Editor, file: PsiFile)
  extends HighlightUsagesHandlerBase[PsiElement](editor, file) {

  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit =
    definition.baseCompanion.map(_.nameId.getPrevSiblingNotWhitespace).foreach { companionKeyword =>
      myReadUsages.add(keyword.getTextRange)
      myReadUsages.add(companionKeyword.getTextRange)
    }

  override def selectTargets(targets: util.List[_ <: PsiElement], selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  override def getTargets: util.List[PsiElement] = Collections.singletonList(keyword)
}