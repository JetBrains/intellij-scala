package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.{findCommonContext, findFirstContext}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.highlightImplicitView
import org.jetbrains.plugins.scala.annotator.createFromUsage._
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, UnresolvedReferenceFixProvider}
import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportTypeFix
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.ValToVarQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedExpressionPrefix, ScInterpolatedPatternPrefix}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.{ReferenceExpressionResolver, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocResolvableCodeReference, ScDocTag}

//noinspection InstanceOf
// TODO unify with ScMethodInvocationAnnotator and ScConstructorInvocationAnnotator
object ScReferenceAnnotator extends ElementAnnotator[ScReference] {

  //By default unresolved scaladoc links are marked as Warnings, which wont be detected in project highlighting tests
  //This flag allows us to automatically test ScalaDoc links resolution during project highlights tests
  @TestOnly var HighlightUnresolvedScalaDocLinksAsErrors = false

  override def annotate(element: ScReference, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware) {
      annotateReference(element)
      qualifierPart(element, typeAware)
    }
  }

  def qualifierPart(element: ScReference, typeAware: Boolean)
                   (implicit holder: ScalaAnnotationHolder): Unit =
    if (!element.getUserData(ReferenceExpressionResolver.ConstructorProxyHolderKey)) {
      val qualifier = element.qualifier
      qualifier match {
        case None =>
          checkNotQualifiedReferenceElement(element, typeAware)
        case Some(_) =>
          checkQualifiedReferenceElement(element, typeAware)
      }
    }

  def annotateReference(reference: ScReference)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val results = reference.multiResolveScala(false)

    if (results.length > 1) {
      return
    }

    for (r <- results) {

      if (r.isAssignment) {
        annotateAssignmentReference(reference)
      }

      val refContext = reference.getContext
      val targetElement = r.element

      if (!r.isApplicable()) {
        targetElement match {
          case f @ (_: ScFunction | _: PsiMethod | _: ScSyntheticFunction) =>
            refContext match {
              case _: ScGenericCall =>
              case _: MethodInvocation =>
              case _ if !reference.is[ScInterpolatedPatternPrefix] =>
                r.problems.foreach {
                  case MissedParametersClause(_) =>
                    holder.createErrorAnnotation(
                      reference,
                      ScalaBundle.message("annotator.error.missing.arguments.for.method", nameWithSignature(f)),
                      createFixesByUsages(reference)
                    )
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
      val maybeFix = ref.get match {
        case ScalaPsiUtil.inNameContext(v: ScValue) =>
          Some(new ValToVarQuickFix(v))
        case _ => None
      }
      holder.createErrorAnnotation(reference, ScalaBundle.message("annotator.error.reassignment.to.val"), maybeFix)
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
      case scalaDocRef: ScDocResolvableCodeReference =>
        annotateScalaDocReference(scalaDocRef, resolve)
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
            case ScInfixPattern(_, `refElement`, _) if refElement.isInstanceOf[ScStableCodeReference] =>
              // todo: this is hide A op B in patterns
              () //skip
            case _: ScImportSelector if resolve.length > 0 =>
              () //skip
            case tag: ScDocTag =>
              if (MyScaladocParsing.ParamOrTParamTags.contains(tag.name)) {
                //skip, references to parameters in ScalaDoc tags are handled in [[org.jetbrains.plugins.scala.codeInspection.scaladoc.ScalaDocReferenceInspection]]
              }
              else {
                holder.createWeakWarningAnnotation(refElement, ScalaBundle.message("cannot.resolve", refElement.refName))
              }
            case _ =>
              addUnknownSymbolProblem()
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
              r.getActualElement.nameContext match {
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

    checkAccessForReference(resolve, refElement)

    if (resolve.length == 1) {
      val resolveResult = resolve(0)
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
          e.getParent.asInstanceOf[ScPrefixExpr].operation == e =>
          resolveResult.implicitFunction match {
            case Some(_) =>
              highlightImplicitMethod(refElement)
            case _ =>
          }
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
          e.getParent.asInstanceOf[ScInfixExpr].operation == e =>
          resolveResult.implicitFunction match {
            case Some(_) =>
              highlightImplicitMethod(refElement)
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
          val message = errorWithRefName(refElement.refName)
          val typeDefFix = refWithoutArgs match {
            case ResolvesTo(obj: ScObject) => fix(obj) :: Nil
            case InstanceOfClass(td: ScTypeDefinition) => fix(td) :: Nil
            case _ => Nil
          }

          holder.createErrorAnnotation(
            refElement.nameId,
            message,
            ReportHighlightingErrorQuickFix :: typeDefFix
          )

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
        case (p: ScPattern) & (_: ScConstructorPattern | _: ScInfixPattern) =>
          val errorWithRefName: String => String = ScalaBundle.message("cannot.resolve.unapply.method", _)
          if (addCreateApplyOrUnapplyFix(errorWithRefName, td => new CreateUnapplyQuickFix(td, p))) return
        case scalaDocTag: ScDocTag if scalaDocTag.getName == MyScaladocParsing.THROWS_TAG =>
          return //see SCL-9490
        case _ =>
      }
    }
  }

  private def createUnknownSymbolProblem(reference: ScReference)
                                        (implicit holder: ScalaAnnotationHolder): Unit = {
    val identifier = reference.nameId
    val fixes =
      UnresolvedReferenceFixProvider.fixesFor(reference) :+
        ReportHighlightingErrorQuickFix :++
        createFixesByUsages(reference) // TODO We can now use UnresolvedReferenceFixProvider to decoupte custom fixes from the annotator

    holder.createErrorAnnotation(
      identifier,
      ScalaBundle.message("cannot.resolve", identifier.getText),
      ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
      fixes
    )
  }

  private def annotateScalaDocReference(scalaDocRef: ScDocResolvableCodeReference, resolveResult: Array[ScalaResolveResult])
                                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val isRootRef = !scalaDocRef.getParent.is[ScDocResolvableCodeReference]
    if (resolveResult.isEmpty && isRootRef) {
      val elementToAnnotate = scalaDocRef.nameId
      val message = ScalaBundle.message("cannot.resolve", elementToAnnotate.getText)
      val fix = ScalaImportTypeFix(scalaDocRef)
      if (HighlightUnresolvedScalaDocLinksAsErrors) {
        holder.createErrorAnnotation(elementToAnnotate, message, fix)
      } else {
        holder.createWarningAnnotation(elementToAnnotate, message, fix)
      }
    }
  }


  private def checkQualifiedReferenceElement(refElement: ScReference, typeAware: Boolean)
                                            (implicit holder: ScalaAnnotationHolder): Unit = {
    val resolve = refElement.multiResolveScala(false)

    checkAccessForReference(resolve, refElement)
    val resolveCount = resolve.length
    if (refElement.isInstanceOf[ScExpression] && resolveCount == 1) {
      val resolveResult = resolve(0)
      resolveResult.implicitFunction match {
        case Some(_) =>
          highlightImplicitMethod(refElement)
        case _ =>
      }
    }

    refElement match {
      case scalaDocRef: ScDocResolvableCodeReference =>
        annotateScalaDocReference(scalaDocRef, resolve)
        return
      case _ =>
    }

    if (refElement.isSoft)
      return

    if (typeAware && resolveCount != 1) {

      refElement.getParent match {
        case _: ScImportSelector | _: ScImportExpr if resolveCount > 0 =>
          return
        case _: ScMethodCall if resolveCount > 1 =>
          val error = ScalaBundle.message("cannot.resolve.overloaded", refElement.refName)
          holder.createErrorAnnotation(refElement.nameId, error)
        case _ =>
          createUnknownSymbolProblem(refElement)
      }
    }
  }

  def nameWithSignature(f: PsiNamedElement): String = nameOf(f) + signatureOf(f)

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

  private def highlightImplicitMethod(refElement: ScReference)
                                     (implicit holder: ScalaAnnotationHolder): Unit = {
    highlightImplicitView(refElement.nameId)
  }

  private def checkAccessForReference(resolve: Array[ScalaResolveResult], refElement: ScReference)
                                     (implicit holder: ScalaAnnotationHolder): Unit = {
    if (refElement.isInstanceOf[ScDocResolvableCodeReference]) { //TODO
      return
    }
    if (refElement.isSoft)
      return
    if (resolve.length != 1)
      return

    resolve(0) match {
      case r if !r.isAccessible =>
        val error = ScalaBundle.message("symbol.is.inaccessible.from.this.place", r.element.name)
        holder.createErrorAnnotation(refElement.nameId, error)
      //todo: add fixes
      case _ =>
    }
  }

  def createFixesByUsages(reference: ScReference): List[CreateFromUsageQuickFixBase] =
    reference match {
      case reference: ScReferenceExpression => createFixesByUsages(reference)
      case reference: ScStableCodeReference => createFixesByUsages(reference)
      case _ => Nil
    }

  private[this] def createFixesByUsages(reference: ScReferenceExpression): List[CreateFromUsageQuickFixBase] =
    reference.getParent match {
      case _: ScMethodCall =>
        val isUpperCased = reference.refName.headOption.exists(_.isUpper)
        new CreateMethodQuickFix(reference) :: List(
          Option.when(isUpperCased)(new CreateCaseClassQuickFix(reference)),
          Option.when(isUpperCased && reference.isInScala3File)(new CreateClassQuickFix(reference)),
        ).flatten
      case ScInfixExpr(_, `reference`, _) =>
        new CreateMethodQuickFix(reference) :: Nil
      case (_: ScGenericCall) childOf (_: ScMethodCall) =>
        new CreateMethodQuickFix(reference) :: Nil
      case _: ScGenericCall =>
        new CreateParameterlessMethodQuickFix(reference) :: Nil
      case _ =>
        new CreateParameterQuickFix(reference) ::
          new CreateParameterlessMethodQuickFix(reference) ::
          new CreateValueQuickFix(reference) ::
          new CreateVariableQuickFix(reference) ::
          new CreateObjectQuickFix(reference) ::
          Nil
    }

  private[this] def createFixesByUsages(reference: ScStableCodeReference): List[CreateTypeDefinitionQuickFix] =
    reference.getParent match {
      case st: ScSimpleTypeElement if st.isSingleton => Nil
      case st: ScSimpleTypeElement if st.annotation =>
        new CreateAnnotationClassQuickFix(reference) ::
          Nil
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
