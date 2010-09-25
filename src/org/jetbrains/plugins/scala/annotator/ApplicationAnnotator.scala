package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

/**
 * Pavel.Fatin, 31.05.2010
 */

trait ApplicationAnnotator {
  def annotateReference(reference: ScReferenceElement, holder: AnnotationHolder) {
    for (result <- reference.multiResolve(false);
         r = result.asInstanceOf[ScalaResolveResult];
         if !r.isApplicable) {
      
      r.element match {
        case f: ScFunction => {
          reference.getContext match {
            case call: ScMethodCall => {
              val missed = for (MissedValueParameter(p) <- r.problems) yield p.name + ": " + p.paramType.presentableText

              if(!missed.isEmpty) holder.createErrorAnnotation(call.args, "Unspecified value parameters: " + missed.mkString(", "))

              r.problems.foreach {
                case DoesNotTakeParameters() =>
                  holder.createErrorAnnotation(call.args, f.name + " does not take parameters")
                case ExcessArgument(argument) =>
                  holder.createErrorAnnotation(argument, "Too many arguments for method " + nameOf(f))
                case TypeMismatch(expression, expectedType) =>
                  for(t <- expression.getType(TypingContext.empty)) {
                    //TODO show parameter name
                    holder.createErrorAnnotation(expression,
                      "Type mismatch, expected: " + expectedType.presentableText + ", actual: " + t.presentableText)
                  }
                case MissedValueParameter(_) => // simultaneously handled above
                case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
                case MalformedDefinition() =>
                  holder.createErrorAnnotation(call.getInvokedExpr, f.name + " has malformed definition")
                case ExpansionForNonRepeatedParameter(expression) =>
                  holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
                case PositionalAfterNamedArgument(argument) =>
                  holder.createErrorAnnotation(argument, "Positional after named argument")
                case ParameterSpecifiedMultipleTimes(assignment) =>
                  holder.createErrorAnnotation(assignment.getLExpression, "Parameter specified multiple times")
                case _ => holder.createErrorAnnotation(call.args, "Not applicable to " + signatureOf(f))
              }
            }
            case _ => {
              r.problems.foreach {
                case MissedParametersClause(clause) =>
                  holder.createErrorAnnotation(reference, "Missing arguments for method " + nameOf(f))
                case _ =>
              }
            }
          }
        }
        case _ =>
      }
    }
  }

  def annotateMethodCall(call: ScMethodCall, holder: AnnotationHolder) {
    //do we need to check it:
    call.getInvokedExpr match {
      case ref: ScReferenceElement =>
        ref.bind match {
          case Some(r) if r.problems.length == 0 => //then it's possibly unhandled case
          case _ => return //it's definetely handled case
        }
      case _ => //handled case (only ref expressions was checked)
    }
    val problems = call.applicationProblems
    val missed = for (MissedValueParameter(p) <- problems) yield p.name + ": " + p.paramType.presentableText

    if(!missed.isEmpty) holder.createErrorAnnotation(call.args, "Unspecified value parameters: " + missed.mkString(", "))

    //todo: better error explanation?
    //todo: duplicate
    problems.foreach {
      case DoesNotTakeParameters() =>
        holder.createErrorAnnotation(call.args, "Application does not take parameters")
      case ExcessArgument(argument) =>
        holder.createErrorAnnotation(argument, "Too many arguments")
      case TypeMismatch(expression, expectedType) =>
        for(t <- expression.getType(TypingContext.empty)) {
          //TODO show parameter name
          holder.createErrorAnnotation(expression,
            "Type mismatch, expected: " + expectedType.presentableText + ", actual: " + t.presentableText)
        }
      case MissedValueParameter(_) => // simultaneously handled above
      case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
      case MalformedDefinition() =>
        holder.createErrorAnnotation(call.getInvokedExpr, "Application has malformed definition")
      case ExpansionForNonRepeatedParameter(expression) =>
        holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
      case PositionalAfterNamedArgument(argument) =>
        holder.createErrorAnnotation(argument, "Positional after named argument")
      case ParameterSpecifiedMultipleTimes(assignment) =>
        holder.createErrorAnnotation(assignment.getLExpression, "Parameter specified multiple times")

      case _ => holder.createErrorAnnotation(call.args, "Not applicable")
    }
  }
  
  private def nameOf(f: ScFunction) = f.name + signatureOf(f)

  private def signatureOf(f: ScFunction): String = {
    if(f.parameters.isEmpty)
      ""
    else
      f.paramClauses.clauses.map(clause => format(clause.parameters, clause.paramTypes)).mkString
  }

  private def format(parameters: Seq[ScParameter], types: Seq[ScType]) = {
    val parts = parameters.zip(types).map {
      case (p, t) => t.presentableText + (if(p.isRepeatedParameter) "*" else "")
    }
    "(" + parts.mkString(", ") + ")"
  }
}