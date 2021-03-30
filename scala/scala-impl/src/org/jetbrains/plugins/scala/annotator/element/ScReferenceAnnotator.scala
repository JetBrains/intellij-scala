package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.{findCommonContext, findFirstContext}
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.{highlightImplicitView, registerTypeMismatchError}
import org.jetbrains.plugins.scala.annotator.createFromUsage._
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportTypeFix
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.ValToVarQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScMethodLike, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedExpressionPrefix, ScInterpolatedPatternPrefix}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocResolvableCodeReference, ScDocTag}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

// TODO unify with ScMethodInvocationAnnotator and ScConstructorInvocationAnnotator
object ScReferenceAnnotator extends ElementAnnotator[ScReference] {

  override def annotate(element: ScReference, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware) {
      annotateReference(element)
    }

    qualifierPart(element, typeAware)
  }

  def qualifierPart(element: ScReference, typeAware: Boolean)
                   (implicit holder: ScalaAnnotationHolder): Unit = {
    element.qualifier match {
      case None => checkNotQualifiedReferenceElement(element, typeAware)
      case Some(_) => checkQualifiedReferenceElement(element, typeAware)
    }
  }

  def annotateReference(reference: ScReference, inDesugaring: Boolean = false)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val results = reference.multiResolveScala(false)

    if (results.length > 1) {
      return
    }

    for (r <- results) {

      UsageTracker.registerUsedImports(reference, r)

      if (r.isAssignment) {
        annotateAssignmentReference(reference)
      }

      val refContext = reference.getContext
      val targetElement = r.element

      def isKindProjector(genericCall: ScGenericCall): Boolean =
        genericCall.kindProjectorPluginEnabled && {
           val refText = genericCall.referencedExpr.getText
            refText == "Lambda" || refText == "λ"
        }
      (targetElement, refContext) match {
        case (typeParamOwner: PsiNamedElement with ScTypeParametersOwner, genericCall: ScGenericCall) if !isKindProjector(genericCall) =>
          ScParameterizedTypeElementAnnotator.annotateTypeArgs(typeParamOwner, genericCall.typeArgs, r.substitutor)
        case _ =>
      }

      if (!r.isApplicable()) {
        targetElement match {
          case Constructor(_) =>
          // don't handle constructors here

          case f @ (_: ScFunction | _: PsiMethod | _: ScSyntheticFunction) =>
            refContext match {
              case genCall: ScGenericCall =>
                /* done by ScParameterizedTypeElementAnnotator.annotateTypeArgs
                val missing = for (MissedTypeParameter(p) <- r.problems) yield p.name
                missing match {
                  case Seq() =>
                  case as =>
                    holder.createErrorAnnotation(genCall.typeArgs,
                      ScalaBundle.message("annotator.error.unspecified.type.parameters", as.mkString(", ")))
                }
                 */
                withoutNonHighlightables(r.problems, holder).foreach {
                  case MissedTypeParameter(_) =>
                  // handled in bulk above
                  case DoesNotTakeTypeParameters =>
                    // handled by ScParameterizedTypeElementAnnotator.annotateTypeArgs
                    //holder.createErrorAnnotation(genCall.typeArgs,
                    //  ScalaBundle.message("annotator.error.does.not.take.type.parameters", f.name))
                  case ExcessTypeArgument(_) =>
                    // handled by ScParameterizedTypeElementAnnotator.annotateTypeArgs
                    //holder.createErrorAnnotation(arg,
                    //  ScalaBundle.message("annotator.error.too.many.type.arguments", f.name))
                  case DefaultTypeParameterMismatch(expected, actual) =>
                    holder.createErrorAnnotation(
                      genCall.typeArgs,
                      ScalaBundle.message("type.mismatch.default.args.expected.actual", expected, actual))
                  case _ =>
                }
              case call: MethodInvocation =>
                implicit val tpc: TypePresentationContext = TypePresentationContext(call)
                val missed =
                  for (MissedValueParameter(p) <- r.problems) yield p.name + ": " + p.paramType.presentableText

                if (missed.nonEmpty) {
                  val range = if (inDesugaring) call.argsElement.getTextRange else {
                    call.argumentExpressions.lastOption
                      .map(e => new TextRange(e.getTextRange.getEndOffset - 1, call.argsElement.getTextRange.getEndOffset))
                      .getOrElse(call.argsElement.getTextRange)
                  }

                  holder.createErrorAnnotation(
                    range,
                    ScalaBundle.message("annotator.error.sunspecified.value.parameters", missed.mkString(", "))
                  ).registerFixesByUsages(reference)
                }
                val (problems, fun) = call.applyOrUpdateElement match {
                  case Some(rr) =>
                    (rr.problems, rr.element)
                  case _ => (r.problems, f)
                }

                val countMatches = !problems.exists(_.is[MissedValueParameter, ExcessArgument])

                var typeMismatchShown = false

                if (!inDesugaring) {
                  val firstExcessiveArgument = problems.filterByType[ExcessArgument].map(_.argument).filter(inSameFile(_, holder)).minByOption(_.getTextOffset)
                  firstExcessiveArgument.foreach { argument =>
                    val opening = argument.prevSiblings.takeWhile(e => e.is[PsiWhiteSpace, PsiComment] || e.textMatches(",") || e.textMatches("(")).lastOption
                    val range = opening.map(e => new TextRange(e.getTextOffset, argument.getTextOffset + 1)).getOrElse(argument.getTextRange)

                    holder.createErrorAnnotation(
                      range,
                      ScalaBundle.message("annotator.error.too.many.arguments.method", nameWithSignature(fun))
                    ).registerFixesByUsages(reference)
                  }
                }

                withoutNonHighlightables(problems, holder).foreach {
                  case DoesNotTakeParameters() =>
                    holder.createErrorAnnotation(
                      call.argsElement,
                      ScalaBundle.message("annotator.error.target.does.not.take.parameters", fun.name)
                    ).registerFixesByUsages(reference)
                  case ExcessArgument(argument) =>
                    if (inDesugaring) {
                      holder.createErrorAnnotation(
                        argument,
                        ScalaBundle.message("annotator.error.too.many.arguments.method", nameWithSignature(fun))
                      ).registerFixesByUsages(reference)
                    }
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
                    holder.createErrorAnnotation(call.getInvokedExpr,
                      ScalaBundle.message("annotator.error.name.has.malformed.definition", name))
                  case ExpansionForNonRepeatedParameter(expression) =>
                    holder.createErrorAnnotation(expression,
                      ScalaBundle.message("annotator.error.expansion.for.non.repeated.parameter"))
                  case PositionalAfterNamedArgument(argument) =>
                    holder.createErrorAnnotation(argument,
                      ScalaBundle.message("annotator.error.positional.after.named.argument"))
                  case ParameterSpecifiedMultipleTimes(assignment) =>
                    holder.createErrorAnnotation(assignment.leftExpression,
                      ScalaBundle.message("annotator.error.parameter.specified.multiple.times"))
                  case WrongTypeParameterInferred => //todo: ?
                  case ExpectedTypeMismatch => //will be reported later

                  case AmbiguousImplicitParameters(_) =>
                  case DefaultTypeParameterMismatch(_, _) =>
                  case DoesNotTakeTypeParameters =>
                  case ExcessTypeArgument(_) =>
                  case MissedParametersClause(_) =>
                  case MissedTypeParameter(_) =>
                  case NotFoundImplicitParameter(_) =>

                  case IncompleteCallSyntax(_) =>
                  case InternalApplicabilityProblem(description) =>
                    throw new AssertionError(
                      "Internal applicability problem should not be get to the annotator, but found: " + description
                    )
                }
              case _ if !reference.is[ScInterpolatedPatternPrefix] =>
                r.problems.foreach {
                  case MissedParametersClause(_) =>
                    holder.createErrorAnnotation(
                      reference,
                      ScalaBundle.message("annotator.error.missing.arguments.for.method", nameWithSignature(f))
                    ).registerFixesByUsages(reference)
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
  private def annotateAssignmentReference(reference: ScReference)
                                         (implicit holder: ScalaAnnotationHolder): Unit = {
    val qualifier = reference.getContext match {
      case x: ScMethodCall => x.getEffectiveInvokedExpr match {
        case x: ScReferenceExpression => x.qualifier
        case _ => None
      }
      case x: ScInfixExpr => Some(x.left)
      case _ => None
    }
    val refElementOpt = qualifier.flatMap(_.asOptionOfUnsafe[ScReference])
    val ref: Option[PsiElement] = refElementOpt.flatMap(_.resolve().toOption)
    val reassignment = ref.exists(ScalaPsiUtil.isReadonly)
    if (reassignment) {
      val annotation = holder.createErrorAnnotation(reference, ScalaBundle.message("annotator.error.reassignment.to.val"))
      ref.get match {
        case named: PsiNamedElement if ScalaPsiUtil.nameContext(named).isInstanceOf[ScValue] =>
          annotation.registerFix(new ValToVarQuickFix(ScalaPsiUtil.nameContext(named).asInstanceOf[ScValue]))
        case _ =>
      }
    }
  }

  private def checkNotQualifiedReferenceElement(refElement: ScReference, typeAware: Boolean)
                                               (implicit holder: ScalaAnnotationHolder): Unit = {
    refElement match {
      case _: ScInterpolatedExpressionPrefix => return // do not inspect interpolated literal, it will be highlighted in other place
      case _ if refElement.isSoft => return
      case _ =>
    }

    val resolve = refElement.multiResolveScala(false)

    refElement match {
      case _: ScDocResolvableCodeReference =>
        if (resolve.isEmpty) {
          val annotation = holder.createAnnotation(
            HighlightSeverity.WARNING,
            refElement.getTextRange,
            ScalaBundle.message("cannot.resolve", refElement.refName)
          )

          annotation.registerFix(ScalaImportTypeFix(refElement))
        }
        return
      case _ =>
    }

    if (resolve.length != 1) {
      def addUnknownSymbolProblem(): Unit = {
        if (resolve.isEmpty) {
          createUnknownSymbolProblem(refElement)
        }
      }

      val parent = refElement.getParent
      refElement match {
        case refElement: ScReferenceExpression =>
          // Let's try to hide dynamic named parameter usage
          refElement.getContext match {
            case assignment@ScAssignment(`refElement`, _) if resolve.isEmpty && assignment.isDynamicNamedAssignment => return
            case _ => addUnknownSymbolProblem()
          }
        case _ =>
          parent match {
            case ScInfixPattern(_, `refElement`, _) if refElement.isInstanceOf[ScStableCodeReference] => // todo: this is hide A op B in patterns
            case _: ScImportSelector if resolve.length > 0 =>
            case _: ScDocTag =>
              holder.createWeakWarningAnnotation(refElement, ScalaBundle.message("cannot.resolve", refElement.refName))
            case _ => addUnknownSymbolProblem()
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
                  val context = findCommonContext(refElement, nameContext)
                  if (context != null) {
                    val neighbour = (findFirstContext(nameContext, false, elem => elem.getContext.eq(context)) match {
                      case s: ScalaPsiElement => s.getDeepSameElementInContext
                      case elem => elem
                    }).getPrevSibling

                    @scala.annotation.tailrec
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

    checkAccessForReference(resolve, refElement)

    if (resolve.length == 1) {
      val resolveResult = resolve(0)
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
          e.getParent.asInstanceOf[ScPrefixExpr].operation == e =>
          resolveResult.implicitFunction match {
            case Some(fun) =>
              val pref = e.getParent.asInstanceOf[ScPrefixExpr]
              val expr = pref.operand
              highlightImplicitMethod(expr, resolveResult, refElement, fun)
            case _ =>
          }
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
          e.getParent.asInstanceOf[ScInfixExpr].operation == e =>
          resolveResult.implicitFunction match {
            case Some(fun) =>
              val inf = e.getParent.asInstanceOf[ScInfixExpr]
              val expr = inf.getBaseExpr
              highlightImplicitMethod(expr, resolveResult, refElement, fun)
            case _ =>
          }
        case _ =>
      }
    }

    def isOnTopLevel(element: PsiElement) = element match {
      case scalaPsi: ScalaPsiElement => !scalaPsi.parents.exists(_.isInstanceOf[ScTypeDefinition])
      case _ => false
    }

    //don't highlight ambiguous definitions, if they are resolved to multiple top-level declarations
    //needed for worksheet and scala notebook files (e.g. zeppelin)
    def isTopLevelResolve =
      resolve.length > 1 && resolve.headOption.map(_.element).filter(isOnTopLevel).exists {
        firstElement =>
          val fFile = firstElement.getContainingFile
          resolve.tail.map(_.element).forall(nextEl => nextEl.getContainingFile == fFile && isOnTopLevel(nextEl))
      }

    if (typeAware && resolve.length != 1 && !(refElement.containingScalaFile.exists(_.isMultipleDeclarationsAllowed) && isTopLevelResolve)) {
      val parent = refElement.getParent
      def addCreateApplyOrUnapplyFix(errorWithRefName: String => String, fix: ScTypeDefinition => IntentionAction): Boolean = {
        val refWithoutArgs = ScalaPsiElementFactory.createReferenceFromText(refElement.getText, parent.getContext, parent)
        if (refWithoutArgs != null && refWithoutArgs.multiResolveScala(false).exists(!_.getElement.isInstanceOf[PsiPackage])) {
          // We can't resolve the method call A(arg1, arg2), but we can resolve A. Highlight this differently.
          val error = errorWithRefName(refElement.refName)
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
        case mc: ScMethodCall if addCreateApplyOrUnapplyFix(
          ScalaBundle.message("cannot.resolve.apply.method", _),
          td => new CreateApplyQuickFix(td, mc)
        ) =>
          return
        case (p: ScPattern) && (_: ScConstructorPattern | _: ScInfixPattern) =>
          val errorWithRefName: String => String = ScalaBundle.message("cannot.resolve.unapply.method", _)
          if (addCreateApplyOrUnapplyFix(errorWithRefName, td => new CreateUnapplyQuickFix(td, p))) return
        case scalaDocTag: ScDocTag if scalaDocTag.getName == MyScaladocParsing.THROWS_TAG => return //see SCL-9490
        case _ =>
      }
    }
  }

  private def createUnknownSymbolProblem(reference: ScReference)
                                        (implicit holder: ScalaAnnotationHolder) = {
    val identifier = reference.nameId
    val annotation = holder.createErrorAnnotation(
      identifier,
      ScalaBundle.message("cannot.resolve", identifier.getText)
    )
    annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)

    val fixes = UnresolvedReferenceFixProvider.fixesFor(reference)

    annotation.registerFixes(fixes)
    annotation.registerFix(ReportHighlightingErrorQuickFix)
    // TODO We can now use UnresolvedReferenceFixProvider to decoupte custom fixes from the annotator
    annotation.registerFixesByUsages(reference)

    annotation
  }

  private def checkQualifiedReferenceElement(refElement: ScReference, typeAware: Boolean)
                                            (implicit holder: ScalaAnnotationHolder): Unit = {
    val resolve = refElement.multiResolveScala(false)

    UsageTracker.registerUsedElementsAndImports(refElement, resolve, checkWrite = true)

    checkAccessForReference(resolve, refElement)
    val resolveCount = resolve.length
    if (refElement.isInstanceOf[ScExpression] && resolveCount == 1) {
      val resolveResult = resolve(0)
      resolveResult.implicitFunction match {
        case Some(fun) =>
          val qualifier = refElement.qualifier.get
          val expr = qualifier.asInstanceOf[ScExpression]
          highlightImplicitMethod(expr, resolveResult, refElement, fun)
        case _ =>
      }
    }

    if (refElement.isInstanceOf[ScDocResolvableCodeReference] && resolveCount > 0 || refElement.isSoft) return
    if (typeAware && resolveCount != 1) {

      refElement.getParent match {
        case _: ScImportSelector | _: ScImportExpr if resolveCount > 0 => return
        case _: ScMethodCall if resolveCount > 1 =>
          val error = ScalaBundle.message("cannot.resolve.overloaded", refElement.refName)
          holder.createErrorAnnotation(refElement.nameId, error)
        case _ => createUnknownSymbolProblem(refElement)
      }
    }
  }

  def nameWithSignature(f: PsiNamedElement) = nameOf(f) + signatureOf(f)

  private def nameOf(f: PsiNamedElement) = f match {
    case m: ScMethodLike if m.isConstructor => m.containingClass.name
    case _                                  => f.name
  }

  private def signatureOf(f: PsiNamedElement): String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(f)
    f match {
      case f: ScMethodLike =>
        if (f.parameters.isEmpty) "" else formatParamClauses(f.parameterList)
      case m: PsiMethod =>
        val params = m.parameters
        if (params.isEmpty) "" else formatJavaParams(params)
      case syn: ScSyntheticFunction =>
        if (syn.paramClauses.isEmpty) "" else syn.paramClauses.map(formatSyntheticParams).mkString
    }
  }

  private def formatParamClauses(paramClauses: ScParameters)(implicit tpc: TypePresentationContext) = {
    def formatParams(parameters: Seq[ScParameter], types: Seq[ScType]) = {
      val parts = parameters.zip(types).map {
        case (p, t) => t.presentableText + (if(p.isRepeatedParameter) "*" else "")
      }
      parenthesise(parts)
    }
    paramClauses.clauses.map(clause => formatParams(clause.parameters, clause.paramTypes)).mkString
  }

  private def formatJavaParams(parameters: Seq[PsiParameter])(implicit tpc: TypePresentationContext): String = {
    val types = parameters.map(_.paramType())
    val parts = parameters.zip(types).map {
      case (p, t) => t.presentableText + (if(p.isVarArgs) "*" else "")
    }
    parenthesise(parts)
  }

  private def formatSyntheticParams(parameters: Seq[Parameter])(implicit tpc: TypePresentationContext): String = {
    val parts = parameters.map {
      p => p.paramType.presentableText + (if (p.isRepeated) "*" else "")
    }
    parenthesise(parts)
  }

  private def parenthesise(items: Seq[_]) = items.mkString("(", ", ", ")")

  // some properties cannot be shown because they are synthetic for example.
  // filter these out
  private def withoutNonHighlightables(problems: Seq[ApplicabilityProblem], holder: ScalaAnnotationHolder)
  : Seq[ApplicabilityProblem] = problems.filter {
    case PositionalAfterNamedArgument(argument) => inSameFile(argument, holder)
    case ParameterSpecifiedMultipleTimes(assignment) => inSameFile(assignment, holder)
    case UnresolvedParameter(assignment) => inSameFile(assignment, holder)
    case ExpansionForNonRepeatedParameter(argument) => inSameFile(argument, holder)
    case ExcessArgument(argument) => inSameFile(argument, holder)
    case MissedParametersClause(clause) => inSameFile(clause, holder)
    case TypeMismatch(expression, _) => inSameFile(expression, holder)
    case ExcessTypeArgument(argument) => inSameFile(argument, holder)
    case MalformedDefinition(_) => true
    case DoesNotTakeParameters() => true
    case MissedValueParameter(_) => true
    case DefaultTypeParameterMismatch(_, _) => true
    case WrongTypeParameterInferred => true
    case DoesNotTakeTypeParameters => true
    case MissedTypeParameter(_) => true
    case ExpectedTypeMismatch => true
    case NotFoundImplicitParameter(_) => true
    case AmbiguousImplicitParameters(_) => true
    case IncompleteCallSyntax(_) => true
    case InternalApplicabilityProblem(_) => true
  }

  private def inSameFile(elem: PsiElement, holder: ScalaAnnotationHolder): Boolean = {
    elem != null && elem.getContainingFile.getViewProvider.getVirtualFile == holder.getCurrentAnnotationSession.getFile.getViewProvider.getVirtualFile
  }

  private def highlightImplicitMethod(expr: ScExpression, resolveResult: ScalaResolveResult, refElement: ScReference,
                                      fun: PsiNamedElement)
                                     (implicit holder: ScalaAnnotationHolder): Unit = {
    val typeTo = resolveResult.implicitType match {
      case Some(tp) => tp
      case _ => Any(expr.projectContext)
    }
    highlightImplicitView(expr, fun, typeTo, refElement.nameId)
  }

  private def checkAccessForReference(resolve: Array[ScalaResolveResult], refElement: ScReference)
                                     (implicit holder: ScalaAnnotationHolder): Unit = {
    if (resolve.length != 1 || refElement.isSoft || refElement.isInstanceOf[ScDocResolvableCodeReference]) return
    resolve(0) match {
      case r if !r.isAccessible =>
        val error = ScalaBundle.message("symbol.is.inaccessible.from.this.place", r.element.name)
        holder.createErrorAnnotation(refElement.nameId, error)
      //todo: add fixes
      case _ =>
    }
  }

  private implicit class ScalaAnnotationExt(private val annotation: ScalaAnnotation) extends AnyVal {

    def registerFixes(fixes: Iterable[IntentionAction]): Unit =
      fixes.foreach(annotation.registerFix)

    def registerFixesByUsages(reference: ScReference): Unit =
      registerFixes(ScalaAnnotationExt.createFixesByUsages(reference))
  }

  private object ScalaAnnotationExt {

    private def createFixesByUsages(reference: ScReference): List[CreateFromUsageQuickFixBase] =
      reference match {
        case reference: ScReferenceExpression => createFixesByUsages(reference)
        case reference: ScStableCodeReference => createFixesByUsages(reference)
        case _ => Nil
      }

    private[this] def createFixesByUsages(reference: ScReferenceExpression): List[CreateFromUsageQuickFixBase] =
      reference.getParent match {
        case _: ScMethodCall =>
          val isUpperCased = reference.refName.headOption.exists(_.isUpper)
          new CreateMethodQuickFix(reference) ::
            (if (isUpperCased)
              new CreateCaseClassQuickFix(reference) :: Nil
            else
              Nil)
        case ScInfixExpr(_, `reference`, _) =>
          new CreateMethodQuickFix(reference) :: Nil
        case (_: ScGenericCall) childOf (_: ScMethodCall) =>
          new CreateMethodQuickFix(reference) :: Nil
        case _: ScGenericCall =>
          new CreateParameterlessMethodQuickFix(reference) :: Nil
        case _ =>
          new CreateParameterlessMethodQuickFix(reference) ::
            new CreateValueQuickFix(reference) ::
            new CreateVariableQuickFix(reference) ::
            new CreateObjectQuickFix(reference) ::
            Nil
      }

    private[this] def createFixesByUsages(reference: ScStableCodeReference): List[CreateTypeDefinitionQuickFix] =
      reference.getParent match {
        case st: ScSimpleTypeElement if st.singleton => Nil
        case pattern@(_: ScConstructorPattern | _: ScInfixPattern) =>
          new CreateCaseClassQuickFix(reference) ::
            new CreateExtractorObjectQuickFix(reference, pattern.asInstanceOf[ScPattern]) ::
            Nil
        case _ =>
          new CreateTraitQuickFix(reference) ::
            new CreateClassQuickFix(reference) ::
            new CreateCaseClassQuickFix(reference) ::
            Nil
      }
  }
}