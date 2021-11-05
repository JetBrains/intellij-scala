package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import java.util
import java.util.Collections

class ScHighlightEndMarkerUsagesHandler(element: ScNamedElement, editor: Editor, file: PsiFile) extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  override def getTargets: util.List[PsiElement] = Collections.singletonList(element.nameId)

  override def selectTargets(targets: util.List[_ <: PsiElement], selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = {
    myReadUsages.add(element.nameId.getTextRange)

    element.containingFile.foreach { file =>
      file.elements
        .filterByType[ScReference]
        .filter(_.isReferenceTo(element))
        .map(_.getTextRange)
        .foreach(myReadUsages.add)
    }
  }
}
