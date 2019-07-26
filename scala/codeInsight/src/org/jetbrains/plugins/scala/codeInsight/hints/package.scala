package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.openapi.editor.colors.EditorColorsScheme
import org.jetbrains.plugins.scala.annotator.TypeDiff
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Group, Match}
import org.jetbrains.plugins.scala.annotator.hints.{Text, foldedAttributes, foldedString}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

package object hints {

  private[hints] object ReferenceName {

    def unapply(expression: ScExpression): Option[(String, Seq[ScExpression])] = expression match {
      case MethodRepr(_, maybeExpression, maybeReference, arguments) =>
        maybeReference.orElse {
          maybeExpression.collect {
            case reference: ScReferenceExpression => reference
          }
        }.map(_.refName -> arguments)
      case _ => None
    }
  }

  private[hints] implicit class CamelCaseExt(private val string: String) extends AnyVal {

    def mismatchesCamelCase(that: String): Boolean =
      camelCaseIterator.zip(that.camelCaseIterator).exists {
        case (leftSegment, rightSegment) => leftSegment != rightSegment
      }

    def camelCaseIterator: Iterator[String] = for {
      name <- ScalaNamesUtil.isBacktickedName(string).iterator
      segment <- name.split("(?<!^)(?=[A-Z])").reverseIterator
    } yield segment.toLowerCase
  }

  private[hints] def textPartsOf(tpe: ScType, maxChars: Int)(implicit scheme: EditorColorsScheme, context: TypePresentationContext): Seq[Text] = {
    def toText(diff: TypeDiff): Text = diff match {
      case Group(diffs @_*) =>
        Text(foldedString,
          foldedAttributes(error = false),
          expansion = Some(() => diffs.map(toText)))
      case Match(text, tpe) =>
        Text(text,
          tooltip = tpe.map(_.canonicalText.replaceFirst("_root_.", "")),
          navigatable = tpe.flatMap(_.extractClass))
    }
    TypeDiff.parse(tpe)
      .flattenTo(maxChars, groupLength = foldedString.length)
      .map(toText)
  }
}
