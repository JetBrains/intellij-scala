package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.NumberLiteralQuickFix._
import org.jetbrains.plugins.scala.annotator.{IntegerKind, Oct, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral.Numeric
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScIntegerLiteral, ScLongLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScPrefixExpr}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel

sealed abstract class ScNumericLiteralAnnotator[L <: Numeric : reflect.ClassTag](isLong: Boolean) extends ElementAnnotator[L] {

  protected def actualAnnotate(literal: L)(implicit holder: ScalaAnnotationHolder): Unit

  override def annotate(literal: L, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val isHandledByScPrefixExprAnnotator =
      ScNumericLiteralAnnotator.integerPrefixElement(literal.getParent).isDefined

    if (!isHandledByScPrefixExprAnnotator) {
      actualAnnotate(literal)
    }
  }
}

object ScNumericLiteralAnnotator {
    private def annotate[L <: Numeric : reflect.ClassTag](literal: L, target: ScExpression, isLong: Boolean)
                                                         (implicit holder: ScalaAnnotationHolder): Option[(ScExpression, Boolean)] = {
    val languageLevel = literal.scalaLanguageLevel

    val text = literal.getLastChild.getText
    val kind = IntegerKind(text)

    if (kind == Oct && languageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_11)) {
      val message = ScalaBundle.message("octal.literals.removed")
      holder.createErrorAnnotation(target.getTextRange, message, new ConvertOctToHex(literal, isLong))
    }

    val number = kind(text, isLong)
    number.lastIndexOf('_') match {
      case -1 =>
      case index =>
        if (languageLevel.exists(_ < ScalaLanguageLevel.Scala_2_13)) {
          holder.createErrorAnnotation(target, ScalaBundle.message("illegal.underscore.separator"))
        }
        if (index == number.length - 1) {
          holder.createErrorAnnotation(target, ScalaBundle.message("trailing.underscore.separator"))
        }
    }

    //Literal can be found in expression and in type
    // 1. In expressions, negative value -42 is represented by:
    //    PrefixExpression
    //      ReferenceExpression: -
    //        IntegerLiteral
    //          PsiElement(integer)
    // 2. In literal type "-" is kept inside the integral literal itself
    //    LiteralType: -42
    //      IntegerLiteral
    //        PsiElement(identifier)
    //          PsiElement(integer)
    val isNegativeExpression = target != literal
    val isNegativeInsideLiteralType = literal match {
      case numeric: Numeric =>
        val c = numeric.getFirstChild
        val startsWithMinus = c != null &&
          c.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER &&
          c.textMatches("-")
        startsWithMinus
      case _ => false
    }
    val isNegative = isNegativeExpression || isNegativeInsideLiteralType
    val maybeNumber = stringToNumber(number, kind, isNegative)()
    if (maybeNumber.isEmpty) {
      holder.createErrorAnnotation(target, ScalaBundle.message("long.literal.is.out.of.range"))
    }

    maybeNumber.map {
      case (_, exceedsIntLimit) => (target, exceedsIntLimit)
    }
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

  def annotateLong(literal: ScLongLiteral, target: ScExpression)
                  (implicit holder: ScalaAnnotationHolder): Unit = {
    annotate(literal, target, isLong = true) match {
      case Some(_) if ConvertMarker.isApplicableTo(literal) =>
        val range = literal.getTextRange
        holder.newAnnotation(HighlightSeverity.WARNING, ScalaBundle.message("lowercase.long.marker"))
          .range(TextRange.from(range.getEndOffset - 1, 1))
          .newFix(new ConvertMarker(literal)).range(range).registerFix
          .create()
      case _ =>
    }
  }

  def annotateInt(literal: ScIntegerLiteral, target: ScExpression)
                 (implicit holder: ScalaAnnotationHolder): Unit = {
    annotate(literal, target, isLong = false) match {
      case Some((target, true)) =>
        val maybeFix = Option.when(target.expectedType().forall(ConvertToLong.isApplicableTo(literal, _))) {
          new ConvertToLong(literal)
        }
        holder.createErrorAnnotation(
          target,
          ScalaBundle.message("integer.literal.is.out.of.range"),
          maybeFix
        )
      case _ =>
    }
  }

  def annotateIntOrLong(literal: Numeric, target: ScExpression)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    literal match {
      case long: ScLongLiteral => annotateLong(long, target)
      case int: ScIntegerLiteral => annotateInt(int, target)
      case _ =>
    }
  }

  def integerPrefixElement(element: PsiElement): Option[(ScPrefixExpr, Numeric)] =
    element match {
      case prefix: ScPrefixExpr =>
        prefix.getChildren match {
          case Array(ElementText("-" | "+"), literal: Numeric) => Some((prefix, literal))
          case _ => None
        }
      case _ => None
    }
}

object ScLongLiteralAnnotator extends ScNumericLiteralAnnotator[ScLongLiteral](isLong = true) {
  protected override def actualAnnotate(literal: ScLongLiteral)(implicit holder: ScalaAnnotationHolder): Unit =
    ScNumericLiteralAnnotator.annotateLong(literal, literal)
}

object ScIntegerLiteralAnnotator extends ScNumericLiteralAnnotator[ScIntegerLiteral](isLong = false) {
  protected override def actualAnnotate(literal: ScIntegerLiteral)(implicit holder: ScalaAnnotationHolder): Unit =
    ScNumericLiteralAnnotator.annotateInt(literal, literal)
}
