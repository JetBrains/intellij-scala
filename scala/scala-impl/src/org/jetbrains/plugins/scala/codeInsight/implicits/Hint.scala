package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor.InlayModel
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.implicits.presentation.Presentation
import org.jetbrains.plugins.scala.extensions.ObjectExt

import scala.collection.JavaConverters._

private case class Hint(presentation: Presentation,
                        element: PsiElement,
                        suffix: Boolean) {

  def addTo(model: InlayModel, combine: Seq[Presentation] => Presentation): Option[Inlay] = {
    val offset = if (suffix) element.getTextRange.getEndOffset else element.getTextRange.getStartOffset

    val existingInlays = model.getInlineElementsInRange(offset, offset).asScala.filter(_.getRenderer.isInstanceOf[PresentationRenderer])

    existingInlays.foreach(_.dispose())

    val aggregatePresentation = {
      val existingPresentations = existingInlays.map(_.getRenderer.asInstanceOf[PresentationRenderer].presentation)
      if (existingInlays.isEmpty) presentation
      else combine(if (suffix) existingPresentations :+ presentation else presentation +: existingPresentations)
    }

    if (ImplicitHints.expanded) {
      presentation.expand(ImplicitHints.ExpansionThreshold)
    }

    val renderer = new PresentationRenderer(aggregatePresentation) // TODO

    //gives more natural behaviour
    val relatesToPrecedingText = false
    Option(model.addInlineElement(offset, relatesToPrecedingText, renderer))
  }

  // We want auto-generate apply() and copy() methods, but reference-based equality
  override def equals(obj: scala.Any): Boolean = obj.asOptionOf[AnyRef].exists(eq)
}
