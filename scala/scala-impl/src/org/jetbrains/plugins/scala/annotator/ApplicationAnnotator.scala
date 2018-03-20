package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.psi.{PsiElement, PsiMethod, PsiNamedElement, PsiParameter}
import org.jetbrains.plugins.scala.annotator.createFromUsage._
import org.jetbrains.plugins.scala.annotator.importsTracker.ImportTracker
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.ValToVarQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPrefixReference
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * Pavel.Fatin, 31.05.2010
 */
trait ApplicationAnnotator {
  def annotateReference(reference: ScReferenceElement, holder: AnnotationHolder) {
    for {r <- reference.multiResolveScala(false)} {

      ImportTracker.registerUsedImports(reference, r)

      if (r.isAssignment) {
        annotateAssignmentReference(reference, holder)
      }
      if (!r.isApplicable()) {
        r.element match {
          case f@(_: ScFunction | _: PsiMethod | _: ScSyntheticFunction) =>
            reference.getContext match {
              case genCall: ScGenericCall =>
                val missing = for (MissedTypeParameter(p) <- r.problems) yield p.name
                missing match {
                  case Seq() =>
                  case as =>
                    holder.createErrorAnnotation(genCall.typeArgs.getOrElse(genCall),
                      "Unspecified type parameters: " + as.mkString(", "))
                }
                r.problems.foreach {
                  case MissedTypeParameter(_) =>
                    // handled in bulk above
                  case DoesNotTakeTypeParameters =>
                    holder.createErrorAnnotation(genCall.typeArgs.getOrElse(genCall), f.name + " does not take type parameters")
                  case ExcessTypeArgument(arg) if inSameFile(arg, holder) =>
                    holder.createErrorAnnotation(arg, "Too many type arguments for " + f.name)
                  case DefaultTypeParameterMismatch(expected, actual) => genCall.typeArgs match {
                    case Some(typeArgs) =>
                      val message: String = ScalaBundle.message("type.mismatch.default.args.expected.actual", expected, actual)
                      holder.createErrorAnnotation(typeArgs, message)
                    case _ =>
                  }
                  case _ =>
                    //holder.createErrorAnnotation(call.argsElement, "Not applicable to " + signatureOf(f))
                }
              case call: MethodInvocation =>
                val missed =
                  for (MissedValueParameter(p) <- r.problems) yield p.name + ": " + p.paramType.presentableText

                if (missed.nonEmpty) {
                  registerCreateFromUsageFixesFor(reference,
                    holder.createErrorAnnotation(call.argsElement,
                      "Unspecified value parameters: " + missed.mkString(", ")))
                }
                val (problems, fun) = call.applyOrUpdateElement match {
                  case Some(rr) =>
                    (rr.problems, rr.element)
                  case _ => (r.problems, f)
                }

                problems.foreach {
                  case DoesNotTakeParameters() =>
                    registerCreateFromUsageFixesFor(reference,
                      holder.createErrorAnnotation(call.argsElement, fun.name + " does not take parameters"))
                  case ExcessArgument(argument) if inSameFile(argument, holder) =>
                    registerCreateFromUsageFixesFor(reference,
                      holder.createErrorAnnotation(argument, "Too many arguments for method " + nameOf(fun)))
                  case TypeMismatch(expression, expectedType) if inSameFile(expression, holder) =>
                    for (t <- expression.`type`()) {
                      //TODO show parameter name
                      val (expectedText, actualText) = ScTypePresentation.different(expectedType, t)
                      val message = ScalaBundle.message("type.mismatch.expected.actual", expectedText, actualText)
                      val annotation = holder.createErrorAnnotation(expression, message)
                      registerCreateFromUsageFixesFor(reference, annotation)
                      annotation.registerFix(ReportHighlightingErrorQuickFix)
                    }

                  case MissedValueParameter(_) => // simultaneously handled above
                  case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
                  case MalformedDefinition() =>
                    holder.createErrorAnnotation(call.getInvokedExpr, f.name + " has malformed definition")
                  case ExpansionForNonRepeatedParameter(expression) if inSameFile(expression, holder) =>
                    holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
                  case PositionalAfterNamedArgument(argument) if inSameFile(argument, holder) =>
                    holder.createErrorAnnotation(argument, "Positional after named argument")
                  case ParameterSpecifiedMultipleTimes(assignment) if inSameFile(assignment, holder) =>
                    holder.createErrorAnnotation(assignment.getLExpression, "Parameter specified multiple times")
                  case WrongTypeParameterInferred => //todo: ?
                  case ExpectedTypeMismatch => //will be reported later
                  case ElementApplicabilityProblem(element, actual, expected) if inSameFile(element, holder) =>
                    val (actualType, expectedType) = ScTypePresentation.different(actual, expected)
                    holder.createErrorAnnotation(element, ScalaBundle.message("type.mismatch.found.required",
                      actualType, expectedType))
                  case _ =>
                    holder.createErrorAnnotation(call.argsElement, "Not applicable to " + signatureOf(f))
                }
              case _ =>
                r.problems.foreach {
                  case MissedParametersClause(_) if !reference.isInstanceOf[ScInterpolatedPrefixReference] =>
                    registerCreateFromUsageFixesFor(reference,
                      holder.createErrorAnnotation(reference, "Missing arguments for method " + nameOf(f)))
                  case _ =>
                }
            }
          case _ =>
        }
      }
    }
  }

  /**
   * Annotates: val a = 1; a += 1;
   */
  private def annotateAssignmentReference(reference: ScReferenceElement, holder: AnnotationHolder) {
    val qualifier = reference.getContext match {
      case x: ScMethodCall => x.getEffectiveInvokedExpr match {
        case x: ScReferenceExpression => x.qualifier
        case _ => None
      }
      case x: ScInfixExpr => Some(x.left)
      case _ => None
    }
    val refElementOpt = qualifier.flatMap(_.asOptionOf[ScReferenceElement])
    val ref: Option[PsiElement] = refElementOpt.flatMap(_.resolve().toOption)
    val reassignment = ref.exists(ScalaPsiUtil.isReadonly)
    if (reassignment) {
      val annotation = holder.createErrorAnnotation(reference, "Reassignment to val")
      ref.get match {
        case named: PsiNamedElement if ScalaPsiUtil.nameContext(named).isInstanceOf[ScValue] =>
          annotation.registerFix(new ValToVarQuickFix(ScalaPsiUtil.nameContext(named).asInstanceOf[ScValue]))
        case _ =>
      }
    }
  }

  def annotateMethodInvocation(call: MethodInvocation, holder: AnnotationHolder) {
    implicit val ctx: ProjectContext = call

    //do we need to check it:
    call.getEffectiveInvokedExpr match {
      case ref: ScReferenceElement =>
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
        val annotation = holder.createErrorAnnotation(call.argsElement, "Application does not take parameters")
        (call, call.getInvokedExpr) match {
          case (c: ScMethodCall, InstanceOfClass(td: ScTypeDefinition)) =>
            annotation.registerFix(new CreateApplyQuickFix(td, c))
          case _ =>
        }
      case ExcessArgument(argument) =>
        holder.createErrorAnnotation(argument, "Too many arguments")
      case TypeMismatch(expression, expectedType) =>
        for (t <- expression.`type`()) {
          //TODO show parameter name
          val (expectedText, actualText) = ScTypePresentation.different(expectedType, t)
          val message = ScalaBundle.message("type.mismatch.expected.actual", expectedText, actualText)
          val annotation = holder.createErrorAnnotation(expression, message)
          annotation.registerFix(ReportHighlightingErrorQuickFix)
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
      case ExpectedTypeMismatch => // it will be reported later
      case DefaultTypeParameterMismatch(_, _) => //it will be reported later
      case _ => holder.createErrorAnnotation(call.argsElement, "Not applicable")
    }
  }

  protected def registerCreateFromUsageFixesFor(ref: ScReferenceElement, annotation: Annotation) {
    ref match {
      case (exp: ScReferenceExpression) childOf (_: ScMethodCall) =>
        annotation.registerFix(new CreateMethodQuickFix(exp))
        if (ref.refName.headOption.exists(_.isUpper))
          annotation.registerFix(new CreateCaseClassQuickFix(exp))
      case (exp: ScReferenceExpression) childOf (infix: ScInfixExpr) if infix.operation == exp =>
        annotation.registerFix(new CreateMethodQuickFix(exp))
      case (exp: ScReferenceExpression) childOf ((_: ScGenericCall) childOf (_: ScMethodCall)) =>
        annotation.registerFix(new CreateMethodQuickFix(exp))
      case (exp: ScReferenceExpression) childOf (_: ScGenericCall) =>
        annotation.registerFix(new CreateParameterlessMethodQuickFix(exp))
      case exp: ScReferenceExpression =>
        annotation.registerFix(new CreateParameterlessMethodQuickFix(exp))
        annotation.registerFix(new CreateValueQuickFix(exp))
        annotation.registerFix(new CreateVariableQuickFix(exp))
        annotation.registerFix(new CreateObjectQuickFix(exp))
      case (_: ScStableCodeReferenceElement) childOf (st: ScSimpleTypeElement) if st.singleton =>
      case (stRef: ScStableCodeReferenceElement) childOf ((p: ScPattern) && (_: ScConstructorPattern | _: ScInfixPattern)) =>
        annotation.registerFix(new CreateCaseClassQuickFix(stRef))
        annotation.registerFix(new CreateExtractorObjectQuickFix(stRef, p))
      case stRef: ScStableCodeReferenceElement =>
        annotation.registerFix(new CreateTraitQuickFix(stRef))
        annotation.registerFix(new CreateClassQuickFix(stRef))
        annotation.registerFix(new CreateCaseClassQuickFix(stRef))
      case _ =>
    }
  }

  private def nameOf(f: PsiNamedElement) = f.name + signatureOf(f)

  private def signatureOf(f: PsiNamedElement): String = f match {
    case f: ScFunction =>
      if (f.parameters.isEmpty) "" else formatParamClauses(f.paramClauses)
    case m: PsiMethod =>
      val params = m.parameters
      if (params.isEmpty) "" else formatJavaParams(params)
    case syn: ScSyntheticFunction =>
      if (syn.paramClauses.isEmpty) "" else syn.paramClauses.map(formatSyntheticParams).mkString
  }

  private def formatParamClauses(paramClauses: ScParameters) = {
    def formatParams(parameters: Seq[ScParameter], types: Seq[ScType]) = {
      val parts = parameters.zip(types).map {
        case (p, t) => t.presentableText + (if(p.isRepeatedParameter) "*" else "")
      }
      parenthesise(parts)
    }
    paramClauses.clauses.map(clause => formatParams(clause.parameters, clause.paramTypes)).mkString
  }

  private def formatJavaParams(parameters: Seq[PsiParameter]): String = {
    val types = ScalaPsiUtil.mapToLazyTypesSeq(parameters)
    val parts = parameters.zip(types).map {
      case (p, t) => t().presentableText + (if(p.isVarArgs) "*" else "")
    }
    parenthesise(parts)
  }

  private def formatSyntheticParams(parameters: Seq[Parameter]) = {
    val parts = parameters.map {
      p => p.paramType.presentableText + (if (p.isRepeated) "*" else "")
    }
    parenthesise(parts)
  }

  private def parenthesise(items: Seq[_]) = items.mkString("(", ", ", ")")

  private def inSameFile(elem: PsiElement, holder: AnnotationHolder): Boolean = {
    elem != null && elem.getContainingFile == holder.getCurrentAnnotationSession.getFile
  }
}
