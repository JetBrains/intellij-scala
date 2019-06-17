package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral.Numeric
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScIntegerLiteral, ScLongLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPrefixExpr

object ScNumericLiteralAnnotator extends ElementAnnotator[Numeric] {

  import project.ScalaLanguageLevel._
  import quickfix.NumberLiteralQuickFix._

  override def annotate(literal: Numeric, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = literal match {
    case _: ScLongLiteral => checkIntegerLiteral(literal, isLong = true)
    case _: ScIntegerLiteral => checkIntegerLiteral(literal, isLong = false)
    case _ =>
  }

  private def checkIntegerLiteral(literal: Numeric, isLong: Boolean) // TODO isLong smells
                                 (implicit holder: AnnotationHolder): Unit = {
    val languageLevel = literal.scalaLanguageLevel

    val text = literal.getLastChild.getText
    val kind = IntegerKind(text)

    val maybeParent = literal.getParent match {
      case prefixExpr: ScPrefixExpr =>
        // only "-1234" is negative, "- 1234" should be considered as positive 1234
        prefixExpr.getChildren match {
          case Array(ElementText("-"), _) => Some(prefixExpr)
          case _ => None
        }
      case _ => None
    }

    kind match {
      case Oct if languageLevel.exists(_ >= Scala_2_11) =>
        createOctToHexAnnotation(
          literal,
          ScalaBundle.message("octal.literals.removed")
        )
        return
      case Oct if languageLevel.contains(Scala_2_10) =>
        createOctToHexAnnotation(
          literal,
          ScalaBundle.message("octal.literals.deprecated"),
          ProblemHighlightType.LIKE_DEPRECATED
        )
      case _ =>
    }

    val number = kind(text, isLong)
    number.lastIndexOf('_') match {
      case -1 =>
      case index =>
        if (languageLevel.exists(_ < Scala_2_13)) {
          holder.createErrorAnnotation(literal, ScalaBundle.message("illegal.underscore.separator"))
        }
        if (index == number.length - 1) {
          holder.createErrorAnnotation(literal, ScalaBundle.message("trailing.underscore.separator"))
        }
    }

    parseIntegerNumber(number, kind, maybeParent.isDefined) match {
      case None =>
        holder.createErrorAnnotation(
          literal,
          ScalaBundle.message("long.literal.is.out.of.range")
        )
      case Some(Right(_)) if !isLong =>
        createToLongAnnotation(
          literal,
          ScalaBundle.message("integer.literal.is.out.of.range"),
          maybeParent
        )
      case _ =>
    }
  }

  private def createOctToHexAnnotation(literal: Numeric, message: String,
                                       highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR)
                                      (implicit holder: AnnotationHolder): Unit = {
    val annotation = highlightType match {
      case ProblemHighlightType.GENERIC_ERROR => holder.createErrorAnnotation(literal, message)
      case _ => holder.createWarningAnnotation(literal, message)
    }
    annotation.setHighlightType(highlightType)
    annotation.registerFix(new ConvertOctToHex(literal))
  }

  private def createToLongAnnotation(literal: Numeric, message: String,
                                     maybeParent: Option[ScPrefixExpr])
                                    (implicit holder: AnnotationHolder): Unit = {
    val expression = maybeParent.getOrElse(literal)
    val annotation = holder.createErrorAnnotation(expression, message)

    val shouldRegisterFix = expression.expectedType().forall {
      ConvertToLong.isApplicableTo(literal, _)
    }

    if (shouldRegisterFix) {
      annotation.registerFix(new ConvertToLong(literal))
    }
  }

  private[this] def parseIntegerNumber(number: String,
                                       kind: IntegerKind,
                                       isNegative: Boolean) =
    stringToNumber(number, kind, isNegative)().map {
      case (value, false) => Left(value.toInt)
      case (value, _) => Right(value)
    }

  @annotation.tailrec
  private[this] def stringToNumber(number: String,
                                   kind: IntegerKind,
                                   isNegative: Boolean)
                                  (index: Int = 0,
                                   value: Long = 0L,
                                   exceedsIntLimit: Boolean = false): Option[(Long, Boolean)] =
    if (index == number.length) {
      val newValue = if (isNegative) -value else value
      Some(newValue, exceedsIntLimit)
    } else {
      number(index) match {
        case '_' => stringToNumber(number, kind, isNegative)(index + 1, value, exceedsIntLimit)
        case char =>
          val digit = char.asDigit
          val IntegerKind(radix, divider) = kind
          val newValue = value * radix + digit

          def exceedsLimit(limit: Long) =
            limit / (radix / divider) < value ||
              limit - (digit / divider) < value * (radix / divider) &&
                !(isNegative && limit == newValue - 1)

          if (value < 0 || exceedsLimit(java.lang.Long.MAX_VALUE)) None
          else stringToNumber(number, kind, isNegative)(
            index + 1,
            newValue,
            value > Integer.MAX_VALUE || exceedsLimit(Integer.MAX_VALUE)
          )
      }
    }
}
