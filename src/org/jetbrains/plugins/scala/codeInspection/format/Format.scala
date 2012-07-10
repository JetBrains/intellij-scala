package org.jetbrains.plugins.scala
package codeInspection.format

import lang.psi.api.expr._
import lang.psi.types.ScType
import lang.psi.types
import com.intellij.psi.{PsiClass, PsiMethod, PsiLiteral, PsiElement}
import lang.psi.api.base.ScLiteral
import extensions.{ContainingClass, PsiReferenceEx, &&}
import lang.psi.api.statements.ScFunction
import lang.psi.api.toplevel.typedef.{ScClass, ScTrait}

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


  def extractFormatCall(element: PsiElement): Option[(ScLiteral, Seq[ScExpression])] = Some(element) collect {
    // "%d".format(1)
    case ScMethodCall(ScReferenceExpression.qualifier(literal: ScLiteral) &&
            PsiReferenceEx.resolve((f: ScFunction) && ContainingClass(owner: ScTrait)), args)
      if literal.isString && isFormatMethod(owner.qualifiedName, f.name) =>
      (literal, args)

    // "%d" format 1
    case ScInfixExpr(literal: ScLiteral, PsiReferenceEx.resolve((f: ScFunction) &&
            ContainingClass(owner: ScTrait)), arg)
      if literal.isString && isFormatMethod(owner.qualifiedName, f.name) =>
      (literal, Seq(arg))

    // 1.formatted("%d")
    case ScMethodCall(ScReferenceExpression.qualifier(arg: ScExpression) &&
            PsiReferenceEx.resolve((f: ScFunction) && ContainingClass(owner: ScClass)), Seq(literal: ScLiteral))
      if literal.isString && isFormattedMethod(owner.qualifiedName, f.name) =>
      (literal, Seq(arg))

    // 1 formatted "%d"
    case ScInfixExpr(arg: ScExpression, PsiReferenceEx.resolve((f: ScFunction) &&
            ContainingClass(owner: ScClass)), literal: ScLiteral)
      if literal.isString && isFormattedMethod(owner.qualifiedName, f.name) =>
      (literal, Seq(arg))

    // String.format("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: PsiMethod) &&
            ContainingClass(owner: PsiClass)), Seq(literal: ScLiteral, args @  _*))
      if literal.isString && isStringFormatMethod(owner.getQualifiedName, f.getName) =>
      (literal, args)
  }

  private def isFormatMethod(holder: String, method: String) =
    holder == "scala.collection.immutable.StringLike" && method == "format"

  private def isFormattedMethod(holder: String, method: String) =
    (holder == "scala.runtime.StringFormat" || holder == "scala.runtime.StringAdd") && method == "formatted"

  private def isStringFormatMethod(holder: String, method: String) =
    holder == "java.lang.String" && method == "format"

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

  def typeFor(format: String): ScType = if (format.isEmpty) types.Any else format.last.toLower match {
    case 'b' => types.Boolean
    case 'c' => types.Char
    case 'd' | 'u' | 'i' | 'o' | 'x'  => types.Int
    case 'f' | 'e' | 'g'  => types.Float
    case 'h' => types.AnyRef
    case 's' => types.Any
    case _ => types.Any
  }

  def formatAsInterpolatedString(parts: Seq[Part]): String = {
    val strings = parts.collect {
      case Text(s) => s
      case Specifier(_, format, expression) =>
        val isConstant = expression.isInstanceOf[ScLiteral]
        val text = if(isConstant) expression.asInstanceOf[ScLiteral].getValue.toString else expression.getText
        val isSimple = text.forall(it => it.isLetter || it.isDigit)
        val expressionText = if (isConstant) text else if (isSimple) "$" + text else "${" + text + "}"
        if (isConstant || format.length == 2) expressionText else expressionText + format
    }
    strings.mkString
  }

  def isFormattingRequired(parts: Seq[Part]): Boolean = {
    parts.exists {
      case Specifier(_, format, _) => format.length > 2
      case _ => false
    }
  }
}
