package org.jetbrains.plugins.scala
package codeInspection.format

import codeInspection.AbstractInspection
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import lang.psi.api.expr._
import lang.psi.api.statements.ScFunction
import extensions._
import lang.psi.api.toplevel.typedef.{ScObject, ScClass, ScTrait}
import lang.psi.api.base.ScLiteral
import codeInspection.format.Format._
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.types
import types.result.TypingContext
import types.Conformance
import com.intellij.psi.{PsiLiteral, PsiClass, PsiMethod}

/**
  // Acceptance test

  // Expression kind
  "value: %d".format("123") // call format
  "value: %d" format "123"  //  infix format
  "123".formatted("%d") // call formatted
  "123" formatted "%d" // infix formatted
  String.format("%d", "123") // java call format
  String format ("%d", "123") // java infix format
  printf("%d", "123") // printf
  System.out.printf("%d", "123") // System.out.printf

  // Warning kind
  "value: %d".format() // no argument
  "value: %1$d".format() // no positional argument
  "value: %d".format("123") // inconvertible type
  "value: ".format(123) // unused argument

  // Multi-line string
  """value: %d""".format("123") // call format

  // Specifier type
  "value: %d".format(123) // call format
  "value: %b".format(true) // call format
  "value: %f".format(0.5F) // call format
  "value: %c".format('c') // call format
  "value: %s".format(123) // call format

 */

/**
 * Pavel Fatin
 */

class MalformedFormatStringInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder) = {
    // "%d".format(1)
    case ScMethodCall(ScReferenceExpression.qualifier(literal: ScLiteral) &&
            PsiReferenceEx.resolve((f: ScFunction) && ContainingClass(owner: ScTrait)), args)
      if literal.isString && isFormatMethod(owner.qualifiedName, f.name) =>
    inspect(literal, args, holder)

    // "%d" format 1
    case ScInfixExpr(literal: ScLiteral, PsiReferenceEx.resolve((f: ScFunction) &&
            ContainingClass(owner: ScTrait)), arg)
      if literal.isString && isFormatMethod(owner.qualifiedName, f.name) =>
    inspect(literal, Seq(arg), holder)

    // 1.formatted("%d")
    case ScMethodCall(ScReferenceExpression.qualifier(arg: ScExpression) &&
            PsiReferenceEx.resolve((f: ScFunction) && ContainingClass(owner: ScClass)), Seq(literal: ScLiteral))
      if literal.isString && isFormattedMethod(owner.qualifiedName, f.name) =>
    inspect(literal, Seq(arg), holder)

    // 1 formatted "%d"
    case ScInfixExpr(arg: ScExpression, PsiReferenceEx.resolve((f: ScFunction) &&
            ContainingClass(owner: ScClass)), literal: ScLiteral)
      if literal.isString && isFormattedMethod(owner.qualifiedName, f.name) =>
    inspect(literal, Seq(arg), holder)

    // String.format("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: PsiMethod) &&
                ContainingClass(owner: PsiClass)), Seq(literal: PsiLiteral, args @  _*))
      if literal.getValue.isInstanceOf[String] && isStringFormatMethod(owner.getQualifiedName, f.getName) =>
    inspect(literal, args, holder)

    // printf("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: ScFunction) &&
            ContainingClass(owner: ScObject)), Seq(literal: ScLiteral, args @  _*))
      if literal.isString && isPrintfMethod(owner.qualifiedName, f.name) =>
    inspect(literal, args, holder)

    // System.out.printf("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: PsiMethod) &&
            ContainingClass(owner: PsiClass)), Seq(literal: PsiLiteral, args @  _*))
      if literal.getValue.isInstanceOf[String] && isPrintStreamPrintfMethod(owner.getQualifiedName, f.getName) =>
    inspect(literal, args, holder)
  }

  def inspect(literal: PsiLiteral, arguments: Seq[ScExpression], holder: ProblemsHolder) {
    val parts = {
      val text = literal.getValue.asInstanceOf[String]
      parseFormatCall(text, arguments)
    }
    parts.foreach {
      case Specifier(Span(start, end), format, argument) =>
        for(argumentType <- argument.getType(TypingContext.empty);
            specifierType = typeFor(format);
            if !Conformance.conforms(specifierType, argumentType)) {
          holder.registerProblem(literal, textRangeIn(literal, start, end),
            "Format specifier %s (%s) cannot be used for an argument %s (%s)".format(
              format, specifierType.presentableText, argument.getText, argumentType.presentableText))
          holder.registerProblem(argument,
            "Argument %s (%s) cannot be used for a format specifier %s (%s)".format(
              argument.getText, argumentType.presentableText, format, specifierType.presentableText))
        }

      case UnboundSpecifier(Span(start, end), format) =>
        holder.registerProblem(literal, textRangeIn(literal, start, end),
          "No argument for a format specifier %s".format(format))

      case UnboundPositionalSpecifier(Span(start, end), position, format) =>
        holder.registerProblem(literal, textRangeIn(literal, start, end),
          "No argument at position %d".format(position))

      case UnusedArgument(argument) =>
        holder.registerProblem(argument, "No format specifer for an argument %s".format(argument.getText),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL)

      case _ =>
    }
  }

  def textRangeIn(literal: PsiLiteral, start: Int, end: Int) = {
    val shift = literal match {
      case it: ScLiteral => if (it.isMultiLineString) 3 else 1
      case _ => 1
    }
    new TextRange(start, end).shiftRight(shift)
  }

  private def isFormatMethod(holder: String, method: String) =
    holder == "scala.collection.immutable.StringLike" && method == "format"

  private def isFormattedMethod(holder: String, method: String) =
      (holder == "scala.runtime.StringFormat" || holder == "scala.runtime.StringAdd") && method == "formatted"

  private def isStringFormatMethod(holder: String, method: String) =
    holder == "java.lang.String" && method == "format"

  private def isPrintStreamPrintfMethod(holder: String, method: String) =
    holder == "java.io.PrintStream" && method == "printf"

  private def isPrintfMethod(holder: String, method: String) =
    holder == "scala.Predef" && method == "printf"
}
