package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.hints.{HintInfo, InlayInfo, Option}
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types.ScType

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

    private[this] val TypeInfoPrefix = "@TYPE@"

    def apply(text: String, anchor: PsiElement, isParameter: Boolean = true): InlayInfo = {
      val textRange = anchor.getTextRange
      val offset = if (isParameter) textRange.getStartOffset else textRange.getEndOffset
      new InlayInfo(text, offset)
    }

    def apply(`type`: ScType, anchor: PsiElement): InlayInfo =
      apply(TypeInfoPrefix + `type`.presentableText, anchor, isParameter = false)

    def presentation(text: String): String = {
      import ScalaTokenTypes.{tASSIGN, tCOLON}
      text.stripPrefix(TypeInfoPrefix) match {
        case `text` => s"$text $tASSIGN"
        case strippedText => s"$tCOLON $strippedText"
      }
    }

  }

}
