package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import java.util
import java.util.Collections

abstract class ScHighlightEndMarkerUsagesHandler private(element: ScalaPsiElement, editor: Editor, file: PsiFile) extends HighlightUsagesHandlerBase[PsiElement](editor, file) {
  protected def elementNameId: PsiElement

  override def getTargets: util.List[PsiElement] = Collections.singletonList(elementNameId)

  override def selectTargets(targets: util.List[_ <: PsiElement], selectionConsumer: com.intellij.util.Consumer[_ >: util.List[_ <: PsiElement]]): Unit = {
    selectionConsumer.consume(targets)
  }

  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = {
    myReadUsages.add(elementNameId.getTextRange)

    element.containingFile.foreach { file =>
      file.elements
        .filterByType[ScReference]
        .filter(_.isReferenceTo(element))
        .map(_.getTextRange)
        .foreach(myReadUsages.add)
    }
  }
}

object ScHighlightEndMarkerUsagesHandler {
  private class NamedHighlightEndMarkerUsagesHandler(element: ScNamedElement, editor: Editor, file: PsiFile)
    extends ScHighlightEndMarkerUsagesHandler(element, editor, file) {
    override protected def elementNameId: PsiElement = element.nameId
  }

  private class StableRefHighlightEndMarkerUsagesHandler(element: ScStableCodeReference, editor: Editor, file: PsiFile)
    extends ScHighlightEndMarkerUsagesHandler(element, editor, file) {
    override protected def elementNameId: PsiElement = element.nameId
  }

  def apply(element: ScNamedElement, editor: Editor, file: PsiFile): ScHighlightEndMarkerUsagesHandler =
    new NamedHighlightEndMarkerUsagesHandler(element, editor, file)

  def apply(element: ScStableCodeReference, editor: Editor, file: PsiFile): ScHighlightEndMarkerUsagesHandler =
    new StableRefHighlightEndMarkerUsagesHandler(element, editor, file)
}
