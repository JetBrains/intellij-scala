package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.{registerTypeMismatchError, withoutNonHighlightables}
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.createFromUsage.{CreateApplyQuickFix, InstanceOfClass}
import org.jetbrains.plugins.scala.annotator.element.ScReferenceAnnotator.{createFixesByUsages, nameWithSignature}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

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

  def annotateMethodInvocation(call: MethodInvocation, inDesugaring: Boolean = false)
                              (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = call
    implicit val tpc: TypePresentationContext = TypePresentationContext(call)

    // this has to be checked in every case
    checkMissingArgumentClauses(call)
    val currentFile = holder.getCurrentAnnotationSession.getFile

    val (elem, problems) = funAndProblemsFor(call)

    val ref = call.getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression]

    val missed = for (MissedValueParameter(p) <- problems) yield p.name + ": " + p.paramType.presentableText


    if(missed.nonEmpty) {
      val range = call.argumentExpressions.lastOption
        .map(e => new TextRange(e.getTextRange.getEndOffset - 1, call.argsElement.getTextRange.getEndOffset))
        .getOrElse(call.argsElement.getTextRange)

      val message = ScalaBundle.message("annotator.error.unspecified.value.parameters", missed.mkString(", "))
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

    val firstExcessiveArgument = problems.filterByType[ExcessArgument].map(_.argument).minByOption(_.getTextOffset)
    firstExcessiveArgument.foreach { argument =>
      val range =
        if (inDesugaring) argument.getTextRange
        else {
          val opening = argument.prevSiblings.takeWhile(e => e.is[PsiWhiteSpace, PsiComment] || e.textMatches(",") || e.textMatches("(")).lastOption
          opening.map(e => new TextRange(e.getTextOffset, argument.getTextOffset + 1)).getOrElse(argument.getTextRange)
        }

      val fixes = ref.toList.flatMap(createFixesByUsages)
      val message = elem.fold(ScalaBundle.message("annotator.error.too.many.arguments")) { elem =>
        ScalaBundle.message("annotator.error.too.many.arguments.method", nameWithSignature(elem))
      }
      holder.createErrorAnnotation(range, message, fixes)
    }

    //todo: better error explanation?
    //todo: duplicate
    withoutNonHighlightables(problems, currentFile).foreach {
      case DoesNotTakeParameters =>
        val targetName = elem.map(_.name)
          .orElse {
            call.getInvokedExpr.`type`().toOption.map("'" + _.presentableText + "'")
          }
          .getOrElse(ScalaBundle.message("does.not.take.parameter.default.target"))

        val message = ScalaBundle.message("annotator.error.target.does.not.take.parameters", targetName)
        val fix = (call, call.getInvokedExpr) match {
          case (c: ScMethodCall, InstanceOfClass(td: ScTypeDefinition)) =>
            Some(new CreateApplyQuickFix(td, c))
          case _ => None
        }
        holder.createErrorAnnotation(call.argsElement, message, fix)
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
      case TypeIsNotStable =>
    }
  }

  private def funAndProblemsFor(call: MethodInvocation): (Option[PsiNamedElement], Seq[ApplicabilityProblem]) = {
    call.applyOrUpdateElement.map(rr => (Some(rr.element), rr.problems))
      .getOrElse {
        call.getEffectiveInvokedExpr match {
          case ref: ScReferenceExpression =>
            ref.bind() match {
              case Some(rr @ ScalaResolveResult(f @ (_: ScFunction | _: PsiMethod | _: ScSyntheticFunction), _)) =>
                (Some(f), rr.problems)
              case _ => (None, call.applicationProblems)
            }
          case _ => (None, call.applicationProblems)
        }
      }
  }

  private def isAmbiguousOverload(problems: Seq[ApplicabilityProblem]): Boolean =
    problems.filterByType[TypeMismatch].groupBy(_.expression).exists(_._2.size > 1)

  @tailrec
  private def isAmbiguousOverload(call: MethodInvocation): Boolean = call.getEffectiveInvokedExpr match {
    case call: MethodInvocation => isAmbiguousOverload(call)
    case reference: ScReference => reference.multiResolveScala(false).length > 1
    case _ => false
  }

  @tailrec
  private def isOuterMostCall(e: PsiElement): Boolean =
    e.getParent match {
      case MethodInvocation(`e`, _) => false
      case p: ScParenthesisedExpr => isOuterMostCall(p)
      case _ => true
    }

  private def countArgumentClauses(call: MethodInvocation): Int = {
    @tailrec
    def inner(expr: ScExpression, acc: Int): Int = expr match {
      case MethodInvocation(expr, _) => inner(expr, acc + 1)
      case ScParenthesisedExpr(expr) => inner(expr, acc)
      case _ => acc
    }

    inner(call, 0)
  }

  private def checkMissingArgumentClauses(call: MethodInvocation)(implicit holder: ScalaAnnotationHolder): Unit = {
    def functionTypeExpected = call.expectedType().exists(FunctionType.isFunctionType)
    def isScala3dotcErrorsMode: Boolean = ScalaHighlightingMode.showCompilerErrorsScala3(call.getProject) && call.isInScala3Module

    if (!isScala3dotcErrorsMode && isOuterMostCall(call) && !functionTypeExpected && !call.parent.exists(_.is[ScUnderscoreSection])) {
      for {
        ref           <- call.getEffectiveInvokedExpr.asOptionOfUnsafe[ScReference]
        resolveResult <- call.applyOrUpdateElement.orElse(ref.bind())
        if !resolveResult.isDynamic
        fun                                  <- resolveResult.element.asOptionOf[ScFunction]
        numArgumentClauses                   = countArgumentClauses(call)

        clauses =
          if (!ResolveUtils.isExtensionMethodCall(ref, fun)) fun.effectiveParameterClauses
          else                                               fun.effectiveParameterClauses.dropWhile(_.owner != fun)

        problems = Compatibility.missedParameterClauseProblemsFor(clauses, numArgumentClauses, isConstructorInvocation = false)
        MissedParametersClause(missedClause) <- problems
      } {
        val endOffset = call.getTextRange.getEndOffset
        val markRange = call
          .asOptionOf[ScMethodCall]
          .map(_.args)
          .flatMap(_.findLastChildByTypeScala[PsiElement](ScalaTokenTypes.tRPARENTHESIS))
          .map(_.getTextRange)
          .getOrElse(TextRange.create(endOffset - 1, endOffset))

        val funNameWithSig = ScReferenceAnnotator.nameWithSignature(fun)
        val message =
          if (missedClause != null)
            ScalaBundle.message("missing.argument.list.for.method.with.explicit.list", missedClause.getText, funNameWithSig)
          else ScalaBundle.message("missing.argument.list.for.method", funNameWithSig)
        holder.createErrorAnnotation(markRange, message)
      }
    }
  }
}
