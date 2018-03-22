package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.StringsExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.ScTypePresentationExt

package object hints {

  private[hints] object InlayInfo {

    private[this] val Ellipsis = "..."

    import ScalaTokenTypes.{tASSIGN, tCOLON}

    def apply(parameter: Parameter, anchor: PsiElement): InlayInfo =
      apply(parameter.name, tASSIGN, anchor)

    def apply(`type`: ScType, anchor: PsiElement)
             (implicit settings: ScalaCodeInsightSettings): InlayInfo = {
      val presentableText = `type` match {
        case PresentableText(Limited(text)) => text
        case FoldedPresentableText(Limited(text)) => text
        case _ => Ellipsis
      }

      apply(presentableText, tCOLON, anchor, relatesToPrecedingText = true)
    }

    private[this] def apply(text: String, token: IElementType, anchor: PsiElement,
                            relatesToPrecedingText: Boolean = false): InlayInfo = {
      val (offset, presentation) = anchor.getTextRange match {
        case range if relatesToPrecedingText => (range.getEndOffset, s"$token $text")
        case range => (range.getStartOffset, s"$text $token")
      }

      new InlayInfo(presentation, offset, false, true, relatesToPrecedingText)
    }

    private[this] object Limited {

      def unapply(text: String)
                 (implicit settings: ScalaCodeInsightSettings): Option[String] =
        if (text.length <= settings.getPresentationLength) Some(text) else None
    }

    private[this] object PresentableText {

      def unapply(`type`: ScType): Some[String] =
        Some(`type`.codeText)
    }

    private[this] object FoldedPresentableText {

      def unapply(`type`: ScType): Option[String] = `type` match {
        case ScCompoundType(Seq(head, _*), _, _) => Some(head.codeText)
        case ScParameterizedType(designator, typeArguments) =>
          val arguments = Seq.fill(typeArguments.size)(Ellipsis)
          Some(s"${designator.codeText}[${arguments.commaSeparated()}]")
        case _ =>
          None
      }
    }
  }

}
