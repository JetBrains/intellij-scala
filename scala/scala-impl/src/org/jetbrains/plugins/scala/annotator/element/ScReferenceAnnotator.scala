package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotation, AnnotationHolder, HighlightSeverity}
import com.intellij.openapi.util.Condition
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.{highlightImplicitView, registerTypeMismatchError}
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.annotator.createFromUsage._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.ValToVarQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScMethodLike, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedPrefixReference, ScInterpolatedStringPartReference}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ScTypePresentation}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{DefaultTypeParameterMismatch, DoesNotTakeParameters, DoesNotTakeTypeParameters, ElementApplicabilityProblem, ExcessArgument, ExcessTypeArgument, ExpansionForNonRepeatedParameter, ExpectedTypeMismatch, MalformedDefinition, MissedParametersClause, MissedTypeParameter, MissedValueParameter, ParameterSpecifiedMultipleTimes, PositionalAfterNamedArgument, ScType, TypeMismatch, UnresolvedParameter, WrongTypeParameterInferred}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocResolvableCodeReference, ScDocTag}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocResolvableCodeReferenceImpl

import scala.collection.Seq

object ScReferenceAnnotator extends ElementAnnotator[ScReference] {
  override def annotate(element: ScReference, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    if (typeAware) {
      annotateReference(element, holder)
    }

    qualifierPart(element, holder, typeAware)
  }

  def qualifierPart(element: ScReference, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    element.qualifier match {
      case None => checkNotQualifiedReferenceElement(element, holder, typeAware)
      case Some(_) => checkQualifiedReferenceElement(element, holder, typeAware)
    }
  }

  def annotateReference(reference: ScReference, holder: AnnotationHolder) {
    for {r <- reference.multiResolveScala(false)} {

      UsageTracker.registerUsedImports(reference, r)

      if (r.isAssignment) {
        annotateAssignmentReference(reference, holder)
      }
      if (!r.isApplicable()) {
        r.element match {
          case Constructor(_) =>
          // don't handle constructors here

          case f@(_: ScFunction | _: PsiMethod | _: ScSyntheticFunction)=>
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
                    expression.`type`().foreach {
                      registerTypeMismatchError(_, expectedType, holder, expression)
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
                    holder.createErrorAnnotation(assignment.leftExpression, "Parameter specified multiple times")
                  case WrongTypeParameterInferred => //todo: ?
                  case ExpectedTypeMismatch => //will be reported later
                  case ElementApplicabilityProblem(element, actual, expected) if inSameFile(element, holder) =>
                    val (actualType, expectedType) = ScTypePresentation.different(actual, expected)
                    holder.createErrorAnnotation(element, ScalaBundle.message("type.mismatch.found.required",
                      actualType, expectedType))
                  case _ =>
                    holder.createErrorAnnotation(call.argsElement, "Not applicable to " + signatureOf(f))
                }
              case _ if !reference.isInstanceOf[ScInterpolatedPrefixReference] =>
                r.problems.foreach {
                  case MissedParametersClause(_) =>
                    registerCreateFromUsageFixesFor(reference,
                      holder.createErrorAnnotation(reference, "Missing arguments for method " + nameOf(f)))
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
      }
    }
  }

  /**
    * Annotates: val a = 1; a += 1;
    */
  private def annotateAssignmentReference(reference: ScReference, holder: AnnotationHolder) {
    val qualifier = reference.getContext match {
      case x: ScMethodCall => x.getEffectiveInvokedExpr match {
        case x: ScReferenceExpression => x.qualifier
        case _ => None
      }
      case x: ScInfixExpr => Some(x.left)
      case _ => None
    }
    val refElementOpt = qualifier.flatMap(_.asOptionOf[ScReference])
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

  private def checkNotQualifiedReferenceElement(refElement: ScReference, holder: AnnotationHolder, typeAware: Boolean) {
    refElement match {
      case _: ScInterpolatedStringPartReference =>
        return //do not inspect interpolated literal, it will be highlighted in other place
      case _ =>
    }

    def getFixes: Seq[IntentionAction] = {
      val classes = ScalaImportTypeFix.getTypesToImport(refElement)
      if (classes.length == 0) return Seq.empty
      Seq(new ScalaImportTypeFix(classes, refElement))
    }

    val resolve = refElement.multiResolveScala(false)
    def processError(countError: Boolean, fixes: => Seq[IntentionAction]) {
      lazy val cachedFixes = fixes
      //todo remove when resolve of unqualified expression will be fully implemented
      if (refElement.getManager.isInProject(refElement) &&
          resolve.length == 0 &&
          (cachedFixes.nonEmpty || countError)) {
        val error = ScalaBundle.message("cannot.resolve", refElement.refName)
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        registerAddFixes(refElement, annotation, cachedFixes: _*)
        annotation.registerFix(ReportHighlightingErrorQuickFix)
        registerCreateFromUsageFixesFor(refElement, annotation)
      }
    }

    if (refElement.isSoft) {
      return
    }

    refElement match {
      case _: ScDocResolvableCodeReference =>
        if (resolve.isEmpty) {
          val annotation = holder.createAnnotation(HighlightSeverity.WARNING,
            refElement.getTextRange,
            ScalaBundle.message("cannot.resolve", refElement.refName))
          registerAddFixes(refElement, annotation, getFixes: _*)
        }
        return

      case _ =>
    }

    if (resolve.length != 1) {
      if (resolve.length == 0) { //Let's try to hide dynamic named parameter usage
        refElement match {
          case e: ScReferenceExpression =>
            e.getContext match {
              case a: ScAssignment if a.leftExpression == e && a.isDynamicNamedAssignment => return
              case _ =>
            }
          case _ =>
        }
      }
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
          e.getParent.asInstanceOf[ScPrefixExpr].operation == e => //todo: this is hide !(Not Boolean)
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
          e.getParent.asInstanceOf[ScInfixExpr].operation == e => //todo: this is hide A op B
        case _: ScReferenceExpression => processError(countError = false, fixes = getFixes)
        case e: ScStableCodeReference if e.getParent.isInstanceOf[ScInfixPattern] &&
          e.getParent.asInstanceOf[ScInfixPattern].operation == e => //todo: this is hide A op B in patterns
        case _ => refElement.getParent match {
          case _: ScImportSelector if resolve.length > 0 =>
          case _ => processError(countError = true, fixes = getFixes)
        }
      }
    } else {
      def showError(): Unit = {
        val error = ScalaBundle.message("forward.reference.detected")
        holder.createErrorAnnotation(refElement.nameId, error)
      }

      refElement.getContainingFile match {
        case file: ScalaFile if !file.allowsForwardReferences =>
          resolve(0) match {
            case r if r.isForwardReference =>
              ScalaPsiUtil.nameContext(r.getActualElement) match {
                case v: ScValue if !v.hasModifierProperty("lazy") => showError()
                case _: ScVariable => showError()
                case nameContext if nameContext.isValid =>
                  //if it has not lazy val or var between reference and statement then it's forward reference
                  val context = PsiTreeUtil.findCommonContext(refElement, nameContext)
                  if (context != null) {
                    val neighbour = (PsiTreeUtil.findFirstContext(nameContext, false, new Condition[PsiElement] {
                      override def value(elem: PsiElement): Boolean = elem.getContext.eq(context)
                    }) match {
                      case s: ScalaPsiElement => s.getDeepSameElementInContext
                      case elem => elem
                    }).getPrevSibling

                    def check(neighbour: PsiElement): Boolean = {
                      if (neighbour == null ||
                        neighbour.getTextRange.getStartOffset <= refElement.getTextRange.getStartOffset) return false
                      neighbour match {
                        case v: ScValue if !v.hasModifierProperty("lazy") => true
                        case _: ScVariable => true
                        case _ => check(neighbour.getPrevSibling)
                      }
                    }
                    if (check(neighbour)) showError()
                  }
              }
            case _ =>
          }
        case _ =>
      }
    }
    UsageTracker.registerUsedElementsAndImports(refElement, resolve, checkWrite = true)

    checkAccessForReference(resolve, refElement, holder)

    if (resolve.length == 1) {
      val resolveResult = resolve(0)
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
          e.getParent.asInstanceOf[ScPrefixExpr].operation == e =>
          resolveResult.implicitFunction match {
            case Some(fun) =>
              val pref = e.getParent.asInstanceOf[ScPrefixExpr]
              val expr = pref.operand
              highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
            case _ =>
          }
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
          e.getParent.asInstanceOf[ScInfixExpr].operation == e =>
          resolveResult.implicitFunction match {
            case Some(fun) =>
              val inf = e.getParent.asInstanceOf[ScInfixExpr]
              val expr = inf.getBaseExpr
              highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
            case _ =>
          }
        case _ =>
      }
    }

    if (typeAware && resolve.length != 1) {
      val parent = refElement.getParent
      def addCreateApplyOrUnapplyFix(messageKey: String, fix: ScTypeDefinition => IntentionAction): Boolean = {
        val refWithoutArgs = ScalaPsiElementFactory.createReferenceFromText(refElement.getText, parent.getContext, parent)
        if (refWithoutArgs != null && refWithoutArgs.multiResolveScala(false).exists(!_.getElement.isInstanceOf[PsiPackage])) {
          // We can't resolve the method call A(arg1, arg2), but we can resolve A. Highlight this differently.
          val error = ScalaBundle.message(messageKey, refElement.refName)
          val annotation = holder.createErrorAnnotation(refElement.nameId, error)
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
          annotation.registerFix(ReportHighlightingErrorQuickFix)
          refWithoutArgs match {
            case ResolvesTo(obj: ScObject) => annotation.registerFix(fix(obj))
            case InstanceOfClass(td: ScTypeDefinition) => annotation.registerFix(fix(td))
            case _ =>
          }
          true
        } else false
      }

      parent match {
        case _: ScImportSelector if resolve.length > 0 => return
        case _: ScMethodCall if resolve.length > 1 =>
          val error = ScalaBundle.message("cannot.resolve.overloaded", refElement.refName)
          holder.createErrorAnnotation(refElement.nameId, error)
        case _: ScMethodCall if resolve.length > 1 =>
          val error = ScalaBundle.message("cannot.resolve.overloaded", refElement.refName)
          holder.createErrorAnnotation(refElement.nameId, error)
        case mc: ScMethodCall if addCreateApplyOrUnapplyFix("cannot.resolve.apply.method", td => new CreateApplyQuickFix(td, mc)) =>
          return
        case (p: ScPattern) && (_: ScConstructorPattern | _: ScInfixPattern) =>
          val messageKey = "cannot.resolve.unapply.method"
          if (addCreateApplyOrUnapplyFix(messageKey, td => new CreateUnapplyQuickFix(td, p))) return
        case scalaDocTag: ScDocTag if scalaDocTag.getName == MyScaladocParsing.THROWS_TAG => return //see SCL-9490
        case _ =>
          val error = ScalaBundle.message("cannot.resolve", refElement.refName)
          val annotation = holder.createErrorAnnotation(refElement.nameId, error)
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          annotation.registerFix(ReportHighlightingErrorQuickFix)
          // TODO We can now use UnresolvedReferenceFixProvider to decoupte custom fixes from the annotator
          registerCreateFromUsageFixesFor(refElement, annotation)
          UnresolvedReferenceFixProvider.implementations
            .foreach(_.fixesFor(refElement).foreach(annotation.registerFix))
      }
    }
  }

  private def checkQualifiedReferenceElement(refElement: ScReference, holder: AnnotationHolder, typeAware: Boolean) {
    val resolve = refElement.multiResolveScala(false)

    UsageTracker.registerUsedElementsAndImports(refElement, resolve, checkWrite = true)

    checkAccessForReference(resolve, refElement, holder)
    val resolveCount = resolve.length
    if (refElement.isInstanceOf[ScExpression] && resolveCount == 1) {
      val resolveResult = resolve(0)
      resolveResult.implicitFunction match {
        case Some(fun) =>
          val qualifier = refElement.qualifier.get
          val expr = qualifier.asInstanceOf[ScExpression]
          highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
        case _ =>
      }
    }

    if (refElement.isInstanceOf[ScDocResolvableCodeReference] && resolveCount > 0 || refElement.isSoft) return
    if (typeAware && resolveCount != 1) {
      if (resolveCount == 1) {
        return
      }

      refElement.getParent match {
        case _: ScImportSelector | _: ScImportExpr if resolveCount > 0 => return
        case _: ScMethodCall if resolveCount > 1 =>
          val error = ScalaBundle.message("cannot.resolve.overloaded", refElement.refName)
          holder.createErrorAnnotation(refElement.nameId, error)
        case _ =>
          val error = ScalaBundle.message("cannot.resolve", refElement.refName)
          val annotation = holder.createErrorAnnotation(refElement.nameId, error)
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          annotation.registerFix(ReportHighlightingErrorQuickFix)
          // TODO We can now use UnresolvedReferenceFixProvider to decoupte custom fixes from the annotator
          registerCreateFromUsageFixesFor(refElement, annotation)
          UnresolvedReferenceFixProvider.implementations
            .foreach(_.fixesFor(refElement).foreach(annotation.registerFix))
      }
    }
  }

  private def nameOf(f: PsiNamedElement) = f.name + signatureOf(f)

  def signatureOf(f: PsiNamedElement): String = f match {
    case f: ScMethodLike =>
      if (f.parameters.isEmpty) "" else formatParamClauses(f.parameterList)
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
    val types = parameters.map(_.paramType())
    val parts = parameters.zip(types).map {
      case (p, t) => t.presentableText + (if(p.isVarArgs) "*" else "")
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

  private def highlightImplicitMethod(expr: ScExpression, resolveResult: ScalaResolveResult, refElement: ScReference,
                                      fun: PsiNamedElement, holder: AnnotationHolder) {
    val typeTo = resolveResult.implicitType match {
      case Some(tp) => tp
      case _ => Any(expr.projectContext)
    }
    highlightImplicitView(expr, fun, typeTo, refElement.nameId, holder)
  }

  private def checkAccessForReference(resolve: Array[ScalaResolveResult], refElement: ScReference, holder: AnnotationHolder) {
    if (resolve.length != 1 || refElement.isSoft || refElement.isInstanceOf[ScDocResolvableCodeReferenceImpl]) return
    resolve(0) match {
      case r if !r.isAccessible =>
        val error = "Symbol %s is inaccessible from this place".format(r.element.name)
        holder.createErrorAnnotation(refElement.nameId, error)
      //todo: add fixes
      case _ =>
    }
  }

  private def registerAddFixes(refElement: ScReference, annotation: Annotation, actions: IntentionAction*) {
    for (action <- actions) {
      annotation.registerFix(action)
    }
  }

  private def registerCreateFromUsageFixesFor(ref: ScReference, annotation: Annotation) {
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
      case (_: ScStableCodeReference) childOf (st: ScSimpleTypeElement) if st.singleton =>
      case (stRef: ScStableCodeReference) childOf ((p: ScPattern) && (_: ScConstructorPattern | _: ScInfixPattern)) =>
        annotation.registerFix(new CreateCaseClassQuickFix(stRef))
        annotation.registerFix(new CreateExtractorObjectQuickFix(stRef, p))
      case stRef: ScStableCodeReference =>
        annotation.registerFix(new CreateTraitQuickFix(stRef))
        annotation.registerFix(new CreateClassQuickFix(stRef))
        annotation.registerFix(new CreateCaseClassQuickFix(stRef))
      case _ =>
    }
  }
}