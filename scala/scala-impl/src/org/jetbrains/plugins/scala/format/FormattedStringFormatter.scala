package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

object FormattedStringFormatter extends StringFormatter {

  // just run the tests...
  override def format(parts: Seq[StringPart]): String = {
    val bindings: Seq[(String, Option[String])] = parts.collect {
      case Text(s0)                             =>
        val s1 = escapePercent(s0)
        val s2 = StringUtil.escapeStringCharacters(s1)
        (s2, None)
      case SpecialFormatEscape(originalText, _) =>
        val s2 = StringUtil.escapeStringCharacters(originalText)
        (s2, None)
      case injection @ Injection(_, specifier)  =>
        injection.expression match {
          case literal: ScLiteral if specifier.isEmpty =>
            val value = literal.getValue.toString
            (escapePercent(value), None)
          case _ =>
            val format = specifier.map(_.format).getOrElse(formatForExpressionType(injection))
            val argument =
              if (injection.isComplexBlock || injection.isLiteral) injection.text
              else injection.value
            (format, Some(argument))
        }
    }
    val (strings, arguments) = bindings.unzip
    val formatString = strings.mkString
    val argumentString = arguments.collect {
      case Some(it) => it
    }
    format(formatString, argumentString.mkString(", "))
  }

  private def escapePercent(text: String): String =
    text.replace("%", "%%")

  private def format(formatter: String, arguments: String) =
    "\"" + formatter + "\"" + ".format(%s)".format(arguments)

  private def formatForExpressionType(injection: Injection) = {
    val specifier = injection.expressionType.map(letterFor)
    "%" + specifier.getOrElse('s')
  }

  private def letterFor(aType: ScType): Char = {
    val stdTypes = aType.projectContext.stdTypes
    import stdTypes._

    aType match {
      case Boolean => 'b'
      case Char => 'c'
      case Byte | Short | Int | Long => 'd'
      case Float | Double => 'f'
      case ScDesignatorType(element) => element.name match {
        case "String" => 's'
        case "BigInt" => 'd'
        case "BigDecimal" => 'f'
        case "Calendar" | "Date" => 't'
        case _ => 's'
      }
      case _ => 's'
    }
  }
}
