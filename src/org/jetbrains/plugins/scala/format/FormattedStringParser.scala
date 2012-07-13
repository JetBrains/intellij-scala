package org.jetbrains.plugins.scala
package format

import lang.psi.api.expr._
import com.intellij.psi.{PsiClass, PsiMethod, PsiElement}
import lang.psi.api.base.ScLiteral
import extensions.{ContainingClass, PsiReferenceEx, &&}
import lang.psi.api.statements.ScFunction
import lang.psi.api.toplevel.typedef.{ScClass, ScTrait}

/**
 * Pavel Fatin
 */

object FormattedStringParser extends StringParser {
  private val FormatSpecifierPattern = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])".r

  def parse(element: PsiElement) = {
    extractFormatCall(element).map(p => parseFormatCall(p._1, p._2))
  }

  def extractFormatCall(element: PsiElement): Option[(ScLiteral, Seq[ScExpression])] = Some(element) collect {
    // "%d".format(1)
    case ScMethodCall(ScReferenceExpression.qualifier(literal: ScLiteral) &&
            PsiReferenceEx.resolve((f: ScFunction) && ContainingClass(owner: ScTrait)), args)
      if literal.isString && isFormatMethod(owner.qualifiedName, f.name) =>
      (literal, args)

    // "%d" format 1, "%d" format (1)
    case ScInfixExpr(literal: ScLiteral, PsiReferenceEx.resolve((f: ScFunction) &&
            ContainingClass(owner: ScTrait)), arg)
      if literal.isString && isFormatMethod(owner.qualifiedName, f.name) =>
      val args = arg match {
        case tuple: ScTuple => tuple.exprs
        case it => Seq(it)
      }
      (literal, args)

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
            ContainingClass(owner: PsiClass)), Seq(literal: ScLiteral, args@_*))
      if literal.isString && isStringFormatMethod(owner.getQualifiedName, f.getName) =>
      (literal, args)
  }

  private def isFormatMethod(holder: String, method: String) =
    holder == "scala.collection.immutable.StringLike" && method == "format"

  private def isFormattedMethod(holder: String, method: String) =
    (holder == "scala.runtime.StringFormat" || holder == "scala.runtime.StringAdd") && method == "formatted"

  private def isStringFormatMethod(holder: String, method: String) =
    holder == "java.lang.String" && method == "format"

  def parseFormatCall(literal: ScLiteral, arguments: Seq[ScExpression]): Seq[StringPart] = {
    val remainingArguments = arguments.toIterator
    val formatString = literal.getValue.asInstanceOf[String]
    val shift = if (literal.isMultiLineString) 3 else 1

    var refferredArguments: List[ScExpression] = Nil

    val bindings = FormatSpecifierPattern.findAllMatchIn(formatString).map { it =>
      val specifier = {
        val span = Span(literal, it.start(0) + shift, it.end(0) + shift)
        val cleanFormat = {
          val format = it.group(0)
          "%" + format.substring(1 + it.end(1) - it.start(1))
        }
        Specifier(span, cleanFormat)
      }
      val positional = it.group(1) != null
      if (positional) {
        val position = it.group(1).dropRight(1).toInt
        arguments.lift(position - 1).map { argument =>
          refferredArguments ::= argument
          Injection(argument, Some(specifier))
        } getOrElse {
          UnboundPositionalSpecifier(specifier, position)
        }
      } else {
        if (remainingArguments.hasNext)
          Injection(remainingArguments.next(), Some(specifier))
        else
          UnboundSpecifier(specifier)
      }
    }

    val texts = FormatSpecifierPattern.split(formatString).map(Text)

    val prefix = intersperse(texts.toList, bindings.toList).filter {
      case Text("") => false
      case _ => true
    }

    val unusedArguments = remainingArguments.filterNot(refferredArguments.contains).map(UnboundExpression).toList

    prefix ++ unusedArguments
  }

  private def intersperse[T](as: List[T], bs: List[T]): List[T] = (as, bs) match {
    case (x :: xs, y :: ys) => x :: y :: intersperse(xs, ys)
    case (xs, Nil) => xs
    case (Nil, ys) => ys
  }
}
