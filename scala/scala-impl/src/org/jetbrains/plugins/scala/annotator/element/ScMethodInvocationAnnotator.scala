package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.registerTypeMismatchError
import org.jetbrains.plugins.scala.annotator.createFromUsage.{CreateApplyQuickFix, InstanceOfClass}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{DefaultTypeParameterMismatch, DoesNotTakeParameters, ExcessArgument, ExpansionForNonRepeatedParameter, ExpectedTypeMismatch, MalformedDefinition, MissedValueParameter, ParameterSpecifiedMultipleTimes, PositionalAfterNamedArgument, TypeMismatch, UnresolvedParameter}
import org.jetbrains.plugins.scala.project.ProjectContext

// TODO Why it's only used for ScMethodCall and ScInfixExp, but not for ScPrefixExp or ScPostfixExpr?
object ScMethodInvocationAnnotator extends ElementAnnotator[MethodInvocation] {
  override def annotate(element: MethodInvocation, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    if (typeAware) {
      annotateMethodInvocation(element, holder)
    }
  }

  def annotateMethodInvocation(call: MethodInvocation, holder: AnnotationHolder) {
    implicit val ctx: ProjectContext = call

    //do we need to check it:
    call.getEffectiveInvokedExpr match {
      case ref: ScReference =>
        ref.bind() match {
          case Some(r) if r.notCheckedResolveResult || r.isDynamic => //it's unhandled case
          case _ =>
            call.applyOrUpdateElement match {
              case Some(r) if r.isDynamic => //it's still unhandled
              case _ => return //it's definetely handled case
            }
        }
      case _ => //unhandled case (only ref expressions was checked)
    }

    val problems = call.applyOrUpdateElement.map(_.problems).getOrElse(call.applicationProblems)
    val missed = for (MissedValueParameter(p) <- problems) yield p.name + ": " + p.paramType.presentableText

    if(missed.nonEmpty)
      holder.createErrorAnnotation(call.argsElement, "Unspecified value parameters: " + missed.mkString(", "))

    //todo: better error explanation?
    //todo: duplicate
    problems.foreach {
      case DoesNotTakeParameters() =>
        val targetName = call.getInvokedExpr.`type`().toOption
          .map("'" + _.presentableText + "'")
          .getOrElse("Application")
        val annotation = holder.createErrorAnnotation(call.argsElement, s"$targetName does not take parameters")
        (call, call.getInvokedExpr) match {
          case (c: ScMethodCall, InstanceOfClass(td: ScTypeDefinition)) =>
            annotation.registerFix(new CreateApplyQuickFix(td, c))
          case _ =>
        }
      case ExcessArgument(argument) =>
        holder.createErrorAnnotation(argument, "Too many arguments")
      case TypeMismatch(expression, expectedType) =>
        expression.`type`().foreach {
          registerTypeMismatchError(_, expectedType, holder, expression)
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
        holder.createErrorAnnotation(assignment.leftExpression, "Parameter specified multiple times")
      case ExpectedTypeMismatch => // it will be reported later
      case DefaultTypeParameterMismatch(_, _) => //it will be reported later
      case _ => holder.createErrorAnnotation(call.argsElement, "Not applicable")
    }
  }
}
