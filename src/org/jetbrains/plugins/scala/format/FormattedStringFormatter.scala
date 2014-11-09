package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScType}

/**
 * Pavel Fatin
 */

object FormattedStringFormatter extends StringFormatter {
  def format(parts: Seq[StringPart]) = {
    val bindings = parts.collect {
      case Text(s) => (StringUtil.escapeStringCharacters(s), None)
      case injection @ Injection(expression, specifier) =>
        if (injection.isLiteral && specifier.isEmpty)
          if (injection.value == "%") ("%%", None)
          else (injection.value, None)
        else {
          val format = specifier.map(_.format)
                  .getOrElse("%" + injection.expressionType.map(letterFor).getOrElse('s'))
          val argument = if (injection.isComplexBlock) injection.text else injection.value
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

  private def format(formatter: String, arguments: String) = {
    '"' + formatter + '"' + ".format(%s)".format(arguments)
  }

  private def letterFor(aType: ScType): Char = aType match {
    case types.Boolean => 'b'
    case types.Char => 'c'
    case types.Byte | types.Short | types.Int | types.Long => 'd'
    case types.Float | types.Double => 'f'
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
