package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.openapi.editor.{Inlay, InlayModel}
import com.intellij.psi.PsiElement

private case class Hint(text: String, element: PsiElement,
                        suffix: Boolean, underlined: Boolean = false,
                        leftGap: Boolean = true, rightGap: Boolean = true) {

  def addTo(model: InlayModel): Inlay = {
    val offset = if (suffix) element.getTextRange.getEndOffset else element.getTextRange.getStartOffset
    val info = new InlayInfo(text, offset, false, true, suffix)
    val renderer = new TextRenderer(info.getText, underlined, leftGap, rightGap)
    model.addInlineElement(info.getOffset, info.getRelatesToPrecedingText, renderer)
  }
}