package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.registerTypeMismatchError
import org.jetbrains.plugins.scala.annotator.createFromUsage.{CreateApplyQuickFix, InstanceOfClass}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, DefaultTypeParameterMismatch, DoesNotTakeParameters, ExcessArgument, ExpansionForNonRepeatedParameter, ExpectedTypeMismatch, MalformedDefinition, MissedValueParameter, ParameterSpecifiedMultipleTimes, PositionalAfterNamedArgument, TypeMismatch, UnresolvedParameter}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.extensions._

import scala.annotation.tailrec

// TODO unify with ScConstructorInvocationAnnotator and ScReferenceAnnotator
// TODO Why it's only used for ScMethodCall and ScInfixExp, but not for ScPrefixExp or ScPostfixExpr?
object ScMethodInvocationAnnotator extends ElementAnnotator[MethodInvocation] {

  override def annotate(element: MethodInvocation, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = {
    if (typeAware) {
      annotateMethodInvocation(element)
    }
  }

  def annotateMethodInvocation(call: MethodInvocation)
                              (implicit holder: AnnotationHolder) {
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

    if(missed.nonEmpty) {
      val range = call.argumentExpressions.lastOption
        .map(e => new TextRange(e.getTextRange.getEndOffset - 1, call.argsElement.getTextRange.getEndOffset))
        .getOrElse(call.argsElement.getTextRange)

      holder.createErrorAnnotation(range, "Unspecified value parameters: " + missed.mkString(", "))
    }

    if (problems.isEmpty) {
      return
    }

    if (isAmbiguousOverload(problems) || isAmbiguousOverload(call)) {
      holder.createErrorAnnotation(call.getEffectiveInvokedExpr, s"Cannot resolve overloaded method")
      return
    }

    val countMatches = !problems.exists(_.is[MissedValueParameter, ExcessArgument])

    var typeMismatchShown = false

    val firstExcessiveArgument = problems.filterBy[ExcessArgument].map(_.argument).firstBy(_.getTextOffset)
    firstExcessiveArgument.foreach { argument =>
      val opening = argument.prevSiblings.takeWhile(e => e.is[PsiWhiteSpace] || e.is[PsiComment] || e.textMatches(",") || e.textMatches("(")).toSeq.lastOption
      val range = opening.map(e => new TextRange(e.getTextOffset, argument.getTextOffset + 1)).getOrElse(argument.getTextRange)
      holder.createErrorAnnotation(range, "Too many arguments")
    }

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
      case ExcessArgument(_) => // simultaneously handled above
      case TypeMismatch(expression, expectedType) =>
        if (countMatches && !typeMismatchShown) {
          expression.`type`().foreach {
            registerTypeMismatchError(_, expectedType, expression)
          }
          typeMismatchShown = true
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

  private def isAmbiguousOverload(problems: Seq[ApplicabilityProblem]): Boolean =
    problems.filterBy[TypeMismatch].groupBy(_.expression).exists(_._2.length > 1)

  @tailrec
  private def isAmbiguousOverload(call: MethodInvocation): Boolean = call.getEffectiveInvokedExpr match {
    case call: MethodInvocation => isAmbiguousOverload(call)
    case reference: ScReference => reference.multiResolveScala(false).length > 1
    case _ => false
  }
}
