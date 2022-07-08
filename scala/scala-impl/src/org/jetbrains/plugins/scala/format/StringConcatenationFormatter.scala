package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.annotator.createFromUsage.TypeAsClass
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.api.ValType
import org.jetbrains.plugins.scala.lang.psi.types.{AnyArrayType, ScLiteralType, ScType}

import scala.annotation.tailrec
import scala.collection.mutable

object StringConcatenationFormatter extends StringFormatter {

  override def format(parts0: Seq[StringPart]): String = {
    val parts = mergeSiblings(parts0.toList)

    if (parts.isEmpty) quoted("") else {
      val concatParts = new mutable.ArrayBuffer[String](parts.size)
      var isFirstOperand = true
      parts.foreach { el =>
        el match {
          case TextLike(text)    =>
            concatParts += quoted(StringUtil.escapeStringCharacters(text))
          case injection: Injection =>
            concatParts += injectionValueForConcatenation(injection, isFirstOperand)
          case _ =>
        }
        isFirstOperand = false
      }
      concatParts.iterator.filter(_.nonEmpty).mkString(" + ")
    }
  }

  /**
   * just run tests...<br>
   * see also [[org.jetbrains.plugins.scala.format.Injection.value]]
   */
  private def injectionValueForConcatenation(injection: Injection, isFirstOperand: Boolean): String = {
    val element: Option[ScExpression] = injection.expression match {
      case block: ScBlockExpr                          =>
        if (block.exprs.size > 1) Some(block)
        else block.exprs.headOption // it can be just s"${}", so need Option[ScExpression]
      case other =>
        Some(other)
    }

    val needsParenthesis = element.exists {
      case _: ScBlockExpr  => false
      case _: ScLiteral |
           _: ScReferenceExpression |
           _: ScMethodCall => false
      case _               => true
    }
    val value = element.map(_.getText).getOrElse("()")
    val valueWithParentheses = if (needsParenthesis) parenthesised(value) else value

    if (value.isEmpty)
      value
    else if (injection.isFormattingRequired)
      """%s.formatted(%s)""".format(valueWithParentheses, quoted(injection.format))
    else if (isFirstOperand)
      addSafeConversionToString(injection.expressionType, value, valueWithParentheses)
    else
      valueWithParentheses
  }

  /**
   * SCL-18608:<br>
   * `s"${42}${str}"`  -> `42.toString + str`<br>
   * `s"${obj}${str}"` -> `String.valueOf(42) + str`<br>
   */
  private def addSafeConversionToString(
    expressionType: Option[ScType],
    value: String,
    valueWithParentheses:  String
  ): String =
    expressionType match {
      case Some(typ) if isString(typ)          => valueWithParentheses
      case Some(_: ValType | _: ScLiteralType) => valueWithParentheses + ".toString"
      // String has overload for `char[]` which shouldn't be used
      case Some(AnyArrayType(arg)) if arg.isChar => s"String.valueOf($value: AnyRef)"
      case _                                     => s"String.valueOf($value)"
    }

  private def isString(typ: ScType): Boolean = {
    val clazz = TypeAsClass.unapply(typ)
    val fqn = clazz.map(_.qualifiedName)
    fqn.contains("java.lang.String")
  }

  // Text(a) :: Text(b) => Text(ab)
  // SpecialFormatEscape.PercentChar :: Text(b) :: SpecialFormatEscape.LineSeparator => Text(%ab\n)
  private def mergeSiblings(parts0: List[StringPart]): List[StringPart] = {
    @tailrec
    def process(in: List[StringPart], acc: List[StringPart]): List[StringPart] =
      in match {
        case TextLike(s1) :: TextLike(s2) :: tail => process(Text(s1 + s2) :: tail, acc)
        case x :: tail                    => process(tail, x :: acc)
        case Nil                          => acc
      }

    process(parts0, Nil).reverse
  }

  private object TextLike {
    def unapply(part: StringPart): Option[String] = part match {
      case Text(s)                           => Some(s)
      case SpecialFormatEscape(_, unescaped) => Some(unescaped)
      case _                                 => None
    }
  }

  private def quoted(s: String) = "\"" + s + "\""
  private def parenthesised(s: String) = "(" + s + ")"
}
