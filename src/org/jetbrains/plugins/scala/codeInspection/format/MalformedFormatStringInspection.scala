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
    // printf("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: ScFunction) &&
            ContainingClass(owner: ScObject)), Seq(literal: ScLiteral, args @  _*))
      if literal.isString && isPrintfMethod(owner.qualifiedName, f.name) =>
    inspect(literal, args, holder)

    // System.out.printf("%d", 1)
    case MethodInvocation(PsiReferenceEx.resolve((f: PsiMethod) &&
            ContainingClass(owner: PsiClass)), Seq(literal: ScLiteral, args @  _*))
      if literal.isString && isPrintStreamPrintfMethod(owner.getQualifiedName, f.getName) =>
    inspect(literal, args, holder)

    // Format calls
    case element => Format.extractFormatCall(element).foreach { p =>
      val (literal, args) = p
      inspect(literal, args, holder)
    }
  }

  def inspect(literal: ScLiteral, arguments: Seq[ScExpression], holder: ProblemsHolder) {
    val parts = {
      val text = literal.getValue.asInstanceOf[String]
      parseFormatCall(text, arguments)
    }
    parts.foreach {
      case Specifier(Span(start, end), format, argument) =>
        for(argumentType <- argument.getType(TypingContext.empty);
            specifierType = typeFor(format) if !Conformance.conforms(specifierType, argumentType)) {
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

  def textRangeIn(literal: ScLiteral, start: Int, end: Int) = {
    val shift = if (literal.isMultiLineString) 3 else 1
    new TextRange(start, end).shiftRight(shift)
  }

  private def isPrintStreamPrintfMethod(holder: String, method: String) =
    holder == "java.io.PrintStream" && method == "printf"

  private def isPrintfMethod(holder: String, method: String) =
    holder == "scala.Predef" && method == "printf"
}
