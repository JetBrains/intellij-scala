package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.editor
import com.intellij.openapi.editor.{Editor, InlayModel}
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.scala.annotator.hints.Hint

import java.awt.Insets
import scala.jdk.CollectionConverters.ListHasAsScala

object ImplicitHint {
  private val EmptyInsets = JBUI.emptyInsets()

  private val ElementKey: Key[PsiElement] = Key.create("SCALA_IMPLICIT_HINT_ELEMENT")

  def elementOf(inlay: Inlay): PsiElement = ElementKey.get(inlay)

  def isImplicitHint(inlay: Inlay): Boolean = inlay.getUserData(ElementKey) != null

  def addTo(hint: Hint, model: InlayModel): Option[Inlay] = {
    import hint._

    val offset = position.getOffset(hint.element) + offsetDelta

    val existingInlays = model.getInlineElementsInRange(offset, offset).asScala.filter(isImplicitHint)
    // Larger priorities appear further left; preserve the order in which hints are collected.
    val priority = -existingInlays.size

    val inlay: editor.Inlay[TextPartsHintRenderer] = {
      val renderer = new TextPartsHintRenderer(parts, menu, hint.corners) {
        override protected def getMargin(editor: Editor): Insets = margin.getOrElse(EmptyInsets)
      }
      if (ImplicitHints.expanded) {
        renderer.expand()
      }
      model.addInlineElement(offset, relatesToPrecedingElement, priority, renderer)
    }
    // Some editors might not support inlay hints
    if (inlay == null)
      return None

    inlay.putUserData(ElementKey, element)
    Some(inlay)
  }
}
