package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.resolve.ScalaResolveResult
import lang.psi.types.result.TypingContext
import quickfix.ReportHighlightingErrorQuickFix
import lang.psi.types._
import lang.psi.api.base.ScConstructor

trait ConstructorAnnotator {
  // TODO duplication with application annotator.
  def annotateConstructor(constructor: ScConstructor, holder: AnnotationHolder) {
    val resolved = constructor.reference.toList.flatMap(_.resolveAllConstructors)

    resolved match {
      case List() =>
        holder.createErrorAnnotation(constructor.typeElement, "Cannot resolve constructor")
      case List(r: ScalaResolveResult) =>

        val missed = for (MissedValueParameter(p) <- r.problems) yield p.name + ": " + p.paramType.presentableText
        val argsElement = constructor.args.getOrElse(constructor.typeElement)
        if (!missed.isEmpty)
          holder.createErrorAnnotation(argsElement,
            "Unspecified value parameters: " + missed.mkString(", "))

        r.problems.foreach {
          case ExcessArgument(argument) =>
            holder.createErrorAnnotation(argument, "Too many arguments for constructor")
          case TypeMismatch(expression, expectedType) =>
            if (expression != null)
              for (t <- expression.getType(TypingContext.empty)) {
                //TODO show parameter name
                val annotation = holder.createErrorAnnotation(expression,
                  "Type mismatch, expected: " + expectedType.presentableText + ", actual: " + t.presentableText)
                annotation.registerFix(ReportHighlightingErrorQuickFix)
              }
            else {
              //TODO investigate case when expression is null. It's possible when new Expression(ScType)
            }
          case MissedValueParameter(_) => // simultaneously handled above
          case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
          case MalformedDefinition() =>
            holder.createErrorAnnotation(constructor.typeElement, "Constructor has malformed definition")
          case ExpansionForNonRepeatedParameter(expression) =>
            holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
          case PositionalAfterNamedArgument(argument) =>
            holder.createErrorAnnotation(argument, "Positional after named argument")
          case ParameterSpecifiedMultipleTimes(assignment) =>
            holder.createErrorAnnotation(assignment.getLExpression, "Parameter specified multiple times")
          case _ => holder.createErrorAnnotation(argsElement, "Not applicable." /* TODO + signatureOf(f)*/)
        }
      case results =>
        holder.createErrorAnnotation(constructor.typeElement, "Cannot resolve constructor")
    }
  }
}