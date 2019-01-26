package org.jetbrains.plugins.scala
package codeInsight

import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
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

}
