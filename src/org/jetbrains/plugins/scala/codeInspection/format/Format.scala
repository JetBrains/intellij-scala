package org.jetbrains.plugins.scala
package codeInspection.format

import lang.psi.api.expr.ScExpression
import lang.psi.types.ScType
import lang.psi.types

/**
 * Pavel Fatin
 */

object Format {
  private val FormatSpecifierPattern = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])".r

  sealed trait Part
  case class Text(s: String) extends Part
  case class Specifier(span: Span, format: String, argument: ScExpression) extends Part
  case class UnboundSpecifier(span: Span, format: String) extends Part
  case class UnboundPositionalSpecifier(span: Span, position: Int, format: String) extends Part
  case class UnusedArgument(expression: ScExpression) extends Part

  case class Span(start: Int, end: Int)

  def parseFormatCall(formatString: String, arguments: Seq[ScExpression]): Seq[Part] = {
    val remainingArguments = arguments.toIterator
    var refferredArguments: List[ScExpression] = Nil

    val bindings = FormatSpecifierPattern.findAllMatchIn(formatString).map { it =>
      val span = Span(it.start(0), it.end(0))
      val format = it.group(0)
      val positional = it.group(1) != null
      if (positional) {
        val cleanFormat = "%" + format.substring(1 + it.end(1) - it.start(1))
        val position = it.group(1).dropRight(1).toInt
        arguments.lift(position - 1).map { argument =>
          refferredArguments ::= argument
          Specifier(span, cleanFormat, argument)
        } getOrElse {
          UnboundPositionalSpecifier(span, position, cleanFormat)
        }
      } else {
        if (remainingArguments.hasNext)
          Specifier(span, format, remainingArguments.next())
        else
          UnboundSpecifier(span, format)
      }
    }

    val texts = FormatSpecifierPattern.split(formatString).map(Text)

    val prefix = intersperse(texts.toList, bindings.toList).filter {
      case Text("") => false
      case _ => true
    }

    val unusedArguments = remainingArguments.filterNot(refferredArguments.contains).map(UnusedArgument).toList

    prefix ++ unusedArguments
  }

  private def intersperse[T](as: List[T], bs: List[T]): List[T] = (as, bs) match {
    case (x :: xs, y :: ys) => x :: y :: intersperse(xs, ys)
    case (xs, Nil) => xs
    case (Nil, ys) => ys
  }

  def typeFor(format: String): ScType = format.last.toLower match {
    case 'b' => types.Boolean
    case 'c' => types.Char
    case 'd' | 'u' | 'i' | 'o' | 'x'  => types.Int
    case 'f' | 'e' | 'g'  => types.Float
    case 'h' => types.AnyRef
    case 's' => types.Any
    case _ => types.Any
  }
}
