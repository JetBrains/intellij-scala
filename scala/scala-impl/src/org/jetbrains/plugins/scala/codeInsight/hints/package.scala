package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.hints.{HintInfo, InlayInfo, Option}
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.collection.JavaConverters

package object hints {

  private[hints] type HintFunction = PartialFunction[PsiElement, Seq[InlayInfo]]

  private[hints] object HintOption {

    def apply(idSegments: Seq[String], defaultValue: Boolean = false): Option = {
      val id = "scala" +: idSegments :+ "hint"
      new Option(id.mkString("."), s"Show ${idSegments.mkString(" ")} hints", defaultValue)
    }
  }

  private[hints] object MethodInfo {

    def apply(method: PsiMethod): HintInfo.MethodInfo = {
      val names = method.parameters.map(_.name)
      import JavaConverters._
      new HintInfo.MethodInfo(methodFqn(method), names.toList.asJava)
    }

    private def methodFqn(method: PsiMethod) = method.getContainingClass match {
      case null => ""
      case clazz => s"${clazz.qualifiedName}.${method.name}"
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
