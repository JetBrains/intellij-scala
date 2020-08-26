package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * Pavel Fatin
 */
object FormattedStringParser extends StringParser {
  private val FormatSpecifierPattern = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])".r

  override def parse(element: PsiElement): Option[Seq[StringPart]] = {
    extractFormatCall(element)
      // string interpolators that are used in interpolated strings
      // have a higher priority over `.format` method call (see SCL-15414)
      // so we shouldn't detect them as formatted
      .filter(!_._1.isInstanceOf[ScInterpolatedStringLiteral])
      .map(p => parseFormatCall(p._1, p._2))
  }

  def extractFormatCall(element: PsiElement): Option[(ScLiteral, collection.Seq[ScExpression])] = Some(element) collect {
    // "%d".format(1)
    case ScMethodCall(ScReferenceExpression.withQualifier(literal: ScLiteral) &&
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
    case ScMethodCall(ScReferenceExpression.withQualifier(arg: ScExpression) &&
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
      if literal.isString && isStringFormatMethod(owner.qualifiedName, f.getName) =>
      (literal, args)
  }

  private def isFormatMethod(holder: String, method: String) =
    holder == "scala.collection.immutable.StringLike" && method == "format"

  private def isFormattedMethod(holder: String, method: String) =
    (holder == "scala.runtime.StringFormat" || holder == "scala.runtime.StringAdd") && method == "formatted"

  private def isStringFormatMethod(holder: String, method: String) =
    holder == "java.lang.String" && method == "format"

  private[format]
  def parseFormatCall(literal: ScLiteral, arguments: collection.Seq[ScExpression]): Seq[StringPart] = {
    val remainingArguments = arguments.toIterator
    val shift = if (literal.isMultiLineString) 3 else 1
    val formatString = literal.getText.drop(shift).dropRight(shift)

    var referredArguments: List[ScExpression] = Nil

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
          referredArguments ::= argument
          Injection(argument, Some(specifier))
        } getOrElse {
          UnboundPositionalSpecifier(specifier, position)
        }
      } else {
        implicit val projectContext: ProjectContext = literal.projectContext
        if (it.toString().equals("%n")) Injection(createExpressionFromText("\"\\n\""), None)
        else if (it.toString == "%%") Injection(createExpressionFromText("\"%\""), None)
        else if (remainingArguments.hasNext) Injection(remainingArguments.next(), Some(specifier))
        else UnboundSpecifier(specifier)
      }
    }

    val texts = FormatSpecifierPattern.split(formatString).map { s =>
      if (literal.isMultiLineString) Text(s) else Text(StringUtil.unescapeStringCharacters(s))
    }

    val prefix = intersperse(texts.toList, bindings.toList).filter {
      case Text("") => false
      case _ => true
    }

    val unusedArguments = remainingArguments.filterNot(referredArguments.contains).map(UnboundExpression).toList

    prefix ++ unusedArguments
  }

  private def intersperse[T](as: List[T], bs: List[T]): List[T] = (as, bs) match {
    case (x :: xs, y :: ys) => x :: y :: intersperse(xs, ys)
    case (xs, Nil) => xs
    case (Nil, ys) => ys
  }
}
