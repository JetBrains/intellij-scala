package org.jetbrains.plugins.scala.format

import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.util.matching.Regex

object FormattedStringParser extends StringParser {

  private val FormatSpecifierWithoutConversionCharPattern = "%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?".r

  /**
   * Parses a formatted string specifier candidate<br>
   * see [[java.util.Formatter]]<br>
   * see  [[https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#syntax]]<br>
   *
   * @note it excludes special escapes "%n" and "%%"
   * @note it also parses invalid specifiers with invalid conversion e.g. "%j"
   * @note yes, space is supported after %: {{{"% (d, % (d".format(1, -1)}}}
   */
  private val FormatSpecifierPattern = (FormatSpecifierWithoutConversionCharPattern.toString + "([a-mo-zA-Z])").r

  private[format] val FormatSpecifierStartPattern = ("^" + FormatSpecifierPattern.toString).r

  /**
   * Can also parse malformed specifiers with invalid (or even missing) conversion char<br>
   * Example:<br>
   * "text %".format()<br>
   * "text %j".format()
   */
  private val FormatSpecifierAcceptingInvalidTypePattern = (FormatSpecifierWithoutConversionCharPattern.toString + ".?").r


  override def parse(element: PsiElement): Option[Seq[StringPart]] = {
    extractFormatCall(element)
      // string interpolators that are used in interpolated strings
      // have a higher priority over `.format` method call (see SCL-15414)
      // so we shouldn't detect them as formatted
      .filter(!_._1.is[ScInterpolatedStringLiteral])
      .map(p => parseFormatCall(p._1, p._2))
  }

  def extractFormatCall(element: PsiElement): Option[(ScStringLiteral, Seq[ScExpression])] = Some(element) collect {
    // "%d".format(1)
    case ScMethodCall(ScReferenceExpression.withQualifier(literal: ScStringLiteral) &
      PsiReferenceEx.resolve((f: ScFunction) & ContainingClass(owner: ScTypeDefinition)), args)
      if isScalaFormatMethod(owner, f.name) =>
      (literal, args)

    // "%d" format 1, "%d" format (1)
    case ScInfixExpr(literal: ScStringLiteral, PsiReferenceEx.resolve((f: ScFunction) & ContainingClass(owner: ScTypeDefinition)), arg)
      if isScalaFormatMethod(owner, f.name) =>
      val args = arg match {
        case tuple: ScTuple => tuple.exprs
        case it => Seq(it)
      }
      (literal, args)

    // 1.formatted("%d")
    case ScMethodCall(ScReferenceExpression.withQualifier(arg: ScExpression) &
      PsiReferenceEx.resolve((f: ScFunction) & ContainingClass(owner: ScTypeDefinition)), Seq(literal: ScStringLiteral))
      if isScalaFormattedMethod(owner, f.name) =>
      (literal, Seq(arg))

    // 1 formatted "%d"
    case ScInfixExpr(arg: ScExpression, PsiReferenceEx.resolve((f: ScFunction) &
      ContainingClass(owner: ScTypeDefinition)), literal: ScStringLiteral)
      if isScalaFormattedMethod(owner, f.name) =>
      (literal, Seq(arg))

    // String.format("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: PsiMethod) &
      ContainingClass(owner: PsiClass)), Seq(literal: ScStringLiteral, args@_*))
      if isStringFormatMethod(owner.qualifiedName, f.getName) =>
      (literal, args)
  }

  private def isScalaFormatMethod(holder: ScTypeDefinition, method: String): Boolean = {
    val fqn = holder.qualifiedName
    holder match {
      case _: ScTrait => // < 2.13
        method == "format" && fqn == "scala.collection.immutable.StringLike"
      case _: ScClass => // since 2.13
        method == "format" && fqn == "scala.collection.StringOps"
      case _ =>
        false
    }
  }

  private def isScalaFormattedMethod(holder: ScTypeDefinition, method: String): Boolean = {
    val fqn = holder.qualifiedName
    holder match {
      case _: ScClass =>
        method == "formatted" && {
          fqn == "scala.runtime.StringFormat" ||
            fqn == "scala.runtime.StringAdd" || // TODO: why StringAdd is here?
            fqn == "scala.Predef.StringFormat"
        }
      case _ => false
    }
  }

  private def isStringFormatMethod(holder: String, method: String) =
    holder == "java.lang.String" && method == "format"

  private[format]
  def parseFormatCall(literal: ScStringLiteral, arguments: Seq[ScExpression]): Seq[StringPart] = {
    val remainingArguments = arguments.iterator
    val shift = if (literal.isMultiLineString) 3 else 1
    val formatString = literal.getText.drop(shift).dropRight(shift)

    var referredArguments: List[ScExpression] = Nil


    /**
     * NOTE: invalid specifiers be actually marked as malformed in the editor
     * in [[org.jetbrains.plugins.scala.codeInspection.format.ScalaMalformedFormatStringInspection]]
     */
    val specifierMatches: Iterator[Regex.Match] = FormatSpecifierAcceptingInvalidTypePattern.findAllMatchIn(formatString)
    val bindings: List[StringPart] = specifierMatches.map { it =>
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
        val argumentOpt = arguments.lift(position - 1)
        argumentOpt.map { argument =>
          referredArguments ::= argument
          Injection(argument, Some(specifier))
        }.getOrElse(UnboundPositionalSpecifier(specifier, position))
      } else {
        import SpecialFormatEscape._
        implicit val projectContext: ProjectContext = literal.projectContext
        val itValue = it.toString
        // NOTE: ideally, %% and %n shouldn't be treated as bindings at all at this stage cause they are not bound to any injection.
        // But for simplicity, I add them as bindings and handle them specially later, when merging with text parts
        if (itValue == LineSeparator.originalText) LineSeparator
        else if (itValue == PercentChar.originalText) PercentChar
        else if (remainingArguments.hasNext) Injection(remainingArguments.next(), Some(specifier))
        else UnboundSpecifier(specifier)
      }
    }.toList

    val regexParts = FormatSpecifierAcceptingInvalidTypePattern.split(formatString)

    assert(!literal.is[ScInterpolatedStringLiteral])
    val isRawContent = literal.isMultiLineString
    val texts: List[Text] = regexParts.map { s0 =>
      val s = ScalaStringUtils.unescapeStringCharacters(s0, isRawContent, noUnicodeEscapesInRawStrings = literal.noUnicodeEscapesInRawStrings)
      Text(s)
    }.toList

    val interparsed = intersperse(texts, bindings)
    val withoutEmpty = interparsed.filter {
      case Text("") => false
      case _ => true
    }
    val prefix = withoutEmpty

    val unusedArguments = remainingArguments.filterNot(referredArguments.contains).map(UnboundExpression).toList

    prefix ++ unusedArguments
  }

  private def intersperse[T](as: List[T], bs: List[T]): List[T] = (as, bs) match {
    case (x :: xs, y :: ys) => x :: y :: intersperse(xs, ys)
    case (xs, Nil) => xs
    case (Nil, ys) => ys
  }
}
