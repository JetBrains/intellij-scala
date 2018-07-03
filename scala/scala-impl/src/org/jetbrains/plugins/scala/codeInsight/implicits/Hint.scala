package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.openapi.editor.{Inlay, InlayModel}
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement

case class Hint(text: String, element: PsiElement,
                suffix: Boolean, underlined: Boolean = false,
                leftGap: Boolean = true, rightGap: Boolean = true,
                menu: Option[String] = None) {

  def addTo(model: InlayModel): Inlay = {
    val offset = if (suffix) element.getTextRange.getEndOffset else element.getTextRange.getStartOffset
    val info = new InlayInfo(text, offset, false, true, suffix)
    val renderer = new TextRenderer(info.getText, underlined, leftGap, rightGap, menu)
    val inlay = model.addInlineElement(info.getOffset, info.getRelatesToPrecedingText, renderer)
    inlay.putUserData(Hint.ElementKey, element)
    inlay
  }
}

object Hint {
  private val ElementKey: Key[PsiElement] = Key.create("SCALA_IMPLICIT_HINT_ELEMENT")

  def elementOf(inlay: Inlay): PsiElement = ElementKey.get(inlay)
}