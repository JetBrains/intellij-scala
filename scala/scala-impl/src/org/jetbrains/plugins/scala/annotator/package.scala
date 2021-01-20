package org.jetbrains.plugins.scala

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.util.text.LiteralFormatUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.xml.util.XmlStringUtil.escapeString
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

package object annotator {
  def conditionalError(
    condition:    PsiElement => Boolean,
    e:            PsiElement,
    @Nls message: String,
    severityF:    HighlightSeverity = HighlightSeverity.WARNING,
    severityT:    HighlightSeverity = HighlightSeverity.ERROR,
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    val severity = if (condition(e)) severityT else severityF
    holder.createAnnotation(severity, e.getTextRange, message)
  }

  def errorIf2_13(e: PsiElement, @Nls message: String)(implicit holder: ScalaAnnotationHolder): Unit =
    conditionalError(_.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_13), e, message)

  private[annotator] sealed abstract class IntegerKind(val radix: Int,
                                                       protected val prefix: String,
                                                       val divider: Int = 2) {

    final def apply(text: String,
                    isLong: Boolean): String = text.substring(
      prefix.length,
      text.length - (if (isLong) 1 else 0)
    )

    final def get: this.type = this

    final def isEmpty: Boolean = false

    final def _1: Int = radix

    final def _2: Int = divider

    final def to(kind: IntegerKind)
                (text: String,
                 isLong: Boolean): String =
      kind.prefix + BigInt(
        apply(LiteralFormatUtil.removeUnderscores(text), isLong),
        radix
      ).toString(kind.radix)
  }

  private[annotator] object IntegerKind {

    def apply(text: String): IntegerKind = text.head match {
      case '0' if text.length > 1 =>
        text(1) match {
          case 'x' | 'X' => Hex
          case 'l' | 'L' => Dec
          case _ => Oct
        }
      case _ => Dec
    }

    def unapply(kind: IntegerKind): IntegerKind = kind
  }

  private[annotator] case object Dec extends IntegerKind(10, "", 1)

  private[annotator] case object Hex extends IntegerKind(16, "0x")

  private[annotator] case object Oct extends IntegerKind(8, "0")


  @Nls
  private[annotator] def tooltipForDiffTrees[T](@Nls error: String, expectedTree: Tree[T], actualType: Tree[T])(isMismatch: T => Boolean, textOf: T => String): String = {
    def format(diff: Tree[T], f: String => String) = {
      val parts = diff.flatten.map { element =>
        val htmlText = escapeString(textOf(element), true)
        if (isMismatch(element)) f(htmlText)
        else htmlText
      }.map {
        "<td style=\"text-align:center\">" + _ + "</td>"
      }
      parts.mkString
    }

    // com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil.redIfNotMatch
    def red(htmlText: String) = {
      val color = if (StartupUiUtil.isUnderDarcula) "FF6B68" else "red"
      "<font color='" + color + "'><b>" + htmlText + "</b></font>"
    }

    ScalaBundle.message(
      "tree.mismatch.tooltip",
      escapeString(error),
      format(expectedTree, s => s"<b>$s</b>"),
      format(actualType, red)
    )
  }
}
