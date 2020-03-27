package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.registerTypeMismatchError
import org.jetbrains.plugins.scala.annotator.createFromUsage.{CreateApplyQuickFix, InstanceOfClass}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

// TODO unify with ScConstructorInvocationAnnotator and ScReferenceAnnotator
// TODO Why it's only used for ScMethodCall and ScInfixExp, but not for ScPrefixExp or ScPostfixExpr?
object ScMethodInvocationAnnotator extends ElementAnnotator[MethodInvocation] {

  override def annotate(element: MethodInvocation, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware) {
      annotateMethodInvocation(element)
    }
  }

  def annotateMethodInvocation(call: MethodInvocation)
                              (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = call
    implicit val tpc: TypePresentationContext = TypePresentationContext(call)

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

      val message = ScalaBundle.message("annotator.error.unspecified.value.parameters.mkstring", missed.mkString(", "))
      holder.createErrorAnnotation(range, message)
    }

    if (problems.isEmpty) {
      return
    }

    if (isAmbiguousOverload(problems) || isAmbiguousOverload(call)) {
      val message = ScalaBundle.message("annotator.error.cannot.resolve.overloaded.method")
      holder.createErrorAnnotation(call.getEffectiveInvokedExpr, message)
      return
    }

    val countMatches = !problems.exists(_.is[MissedValueParameter, ExcessArgument])

    var typeMismatchShown = false

    val firstExcessiveArgument = problems.filterBy[ExcessArgument].map(_.argument).firstBy(_.getTextOffset)
    firstExcessiveArgument.foreach { argument =>
      val opening = argument.prevSiblings.takeWhile(e => e.is[PsiWhiteSpace] || e.is[PsiComment] || e.textMatches(",") || e.textMatches("(")).toSeq.lastOption
      val range = opening.map(e => new TextRange(e.getTextOffset, argument.getTextOffset + 1)).getOrElse(argument.getTextRange)
      holder.createErrorAnnotation(range, ScalaBundle.message("annotator.error.too.many.arguments"))
    }

    //todo: better error explanation?
    //todo: duplicate
    problems.foreach {
      case DoesNotTakeParameters() =>
        val targetName = call.getInvokedExpr.`type`().toOption
          .map("'" + _.presentableText + "'")
          .getOrElse("Application")
        val message = ScalaBundle.message("annotator.error.target.does.not.take.parameters", targetName)
        val annotation = holder.createErrorAnnotation(call.argsElement, message)
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
      case MalformedDefinition(name) =>
        holder.createErrorAnnotation(call.getInvokedExpr, ScalaBundle.message("annotator.error.name.has.malformed.definition", name))
      case ExpansionForNonRepeatedParameter(expression) =>
        holder.createErrorAnnotation(expression, ScalaBundle.message("annotator.error.expansion.for.non.repeated.parameter"))
      case PositionalAfterNamedArgument(argument) =>
        holder.createErrorAnnotation(argument, ScalaBundle.message("annotator.error.positional.after.named.argument"))
      case ParameterSpecifiedMultipleTimes(assignment) =>
        holder.createErrorAnnotation(assignment.leftExpression, ScalaBundle.message("annotator.error.parameter.specified.multiple.times"))
      case ExpectedTypeMismatch => // it will be reported later
      case DefaultTypeParameterMismatch(_, _) => //it will be reported later

      case AmbiguousImplicitParameters(_) =>
      case MissedParametersClause(_) =>
      case DoesNotTakeTypeParameters =>
      case ExcessTypeArgument(_) =>
      case IncompleteCallSyntax(_) =>
      case InternalApplicabilityProblem(_) =>
      case MissedTypeParameter(_) =>
      case NotFoundImplicitParameter(_) =>
      case WrongTypeParameterInferred =>
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
