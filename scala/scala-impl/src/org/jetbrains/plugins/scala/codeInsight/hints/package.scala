package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.hints._
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

package object hints {

  private[hints] object HintOption {

    def apply(defaultValue: Boolean, idSegments: String*): Option = {
      val id = "scala" +: idSegments :+ "hint"
      new Option(id.mkString("."), s"Show ${idSegments.mkString(" ")} hints", defaultValue)
    }
  }

  private[hints] object InlayInfo {

    import ScalaTokenTypes.{tASSIGN, tCOLON}

    def apply(parameter: Parameter, anchor: PsiElement): InlayInfo =
      apply(parameter.name, tASSIGN, anchor)

    def apply(`type`: ScType, anchor: PsiElement): InlayInfo =
      apply(`type`.presentableText, tCOLON, anchor, relatesToPrecedingText = true)

    private[this] def apply(text: String, token: IElementType, anchor: PsiElement,
                            relatesToPrecedingText: Boolean = false): InlayInfo = {
      val (offset, presentation) = anchor.getTextRange match {
        case range if relatesToPrecedingText => (range.getEndOffset, s"$token $text")
        case range => (range.getStartOffset, s"$text $token")
      }

      new InlayInfo(presentation, offset, false, true, relatesToPrecedingText)
    }
  }

}
