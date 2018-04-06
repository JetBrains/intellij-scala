package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

package object hints {

  private[hints] object InlayInfo {

    def apply(text: String, token: IElementType, anchor: PsiElement,
              relatesToPrecedingText: Boolean = false): InlayInfo = {
      val (offset, presentation) = anchor.getTextRange match {
        case range if relatesToPrecedingText => (range.getEndOffset, s"$token $text")
        case range => (range.getStartOffset, s"$text $token")
      }

      new InlayInfo(presentation, offset, false, true, relatesToPrecedingText)
    }
  }
}
