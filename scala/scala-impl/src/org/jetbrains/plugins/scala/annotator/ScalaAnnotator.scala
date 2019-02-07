package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection._
import com.intellij.lang.annotation._
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.{Condition, Key, TextRange}
import com.intellij.psi._
import com.intellij.psi.impl.source.JavaDummyHolder
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import org.jetbrains.plugins.scala.annotator.annotationHolder.{DelegateAnnotationHolder, ErrorIndication}
import org.jetbrains.plugins.scala.annotator.createFromUsage._
import org.jetbrains.plugins.scala.annotator.intention._
import org.jetbrains.plugins.scala.annotator.modifiers.ModifierChecker
import org.jetbrains.plugins.scala.annotator.quickfix._
import org.jetbrains.plugins.scala.annotator.template._
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker._
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.{RemoveValFromForBindingIntentionAction, RemoveValFromGeneratorIntentionAction}
import org.jetbrains.plugins.scala.components.HighlightingAdvisor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.macros.expansion.RecompileAnnotationAction
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameters, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedStringPartReference
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.ProcessSubtypes
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Compatibility, ScType, ScalaType, ValueClassType}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocResolvableCodeReference, ScDocTag}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.impl.ScDocResolvableCodeReferenceImpl
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner, ProjectPsiElementExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, mutable}
import scala.meta.intellij.MetaExpansionsManager

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */
abstract class ScalaAnnotator extends Annotator
  with FunctionAnnotator with ScopeAnnotator
  with ApplicationAnnotator
  with ConstructorAnnotator
  with OverridingAnnotator
  with ProjectContextOwner with DumbAware {

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit = {

    val typeAware = isAdvancedHighlightingEnabled(element)
    val (compiled, isInSources) = element.getContainingFile match {
      case file: ScalaFile =>
        val isInSources = file.getVirtualFile.nullSafe.exists {
          ProjectRootManager.getInstance(file.getProject).getFileIndex.isInSourceContent
        }
        (file.isCompiled, isInSources)
      case _ => (false, false)
    }

    if (isInSources && (element eq element.getContainingFile)) {
      Stats.trigger {
        import FeatureKey._
        if (typeAware) annotatorTypeAware
        else annotatorNotTypeAware
      }
    }

    element match {
      case e: ScalaPsiElement => e.annotate(holder, typeAware)
      case _ =>
    }

    val visitor = new ScalaElementVisitor {
      private def expressionPart(expr: ScExpression) {
        if (!compiled) {
          ImplicitParametersAnnotator.annotate(expr, holder, typeAware)
          ByNameParameter.annotate(expr, holder, typeAware)
        }

        if (isAdvancedHighlightingEnabled(element)) {
          expr.getTypeAfterImplicitConversion() match {
            case ExpressionTypeResult(Right(t), _, Some(implicitFunction)) =>
              highlightImplicitView(expr, implicitFunction.element, t, expr, holder)
            case _ =>
          }
        }
      }

      override def visitExpression(expr: ScExpression) {
        expressionPart(expr)
        super.visitExpression(expr)
      }

      override def visitMacroDefinition(fun: ScMacroDefinition): Unit = {
        Stats.trigger(isInSources, FeatureKey.macroDefinition)
        super.visitMacroDefinition(fun)
      }

      override def visitReferenceExpression(ref: ScReferenceExpression) {
        referencePart(ref)
        visitExpression(ref)
      }

      override def visitGenericCallExpression(call: ScGenericCall) {
        //todo: if (typeAware) checkGenericCallExpression(call, holder)
        super.visitGenericCallExpression(call)
      }

      override def visitForBinding(forBinding: ScForBinding) {
        forBinding.valKeyword match {
          case Some(valKeyword) =>
            val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("enumerator.val.keyword.deprecated"))
            annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
            annotation.registerFix(new RemoveValFromForBindingIntentionAction(forBinding))
          case _ =>
        }
        super.visitForBinding(forBinding)
      }

      private def checkGenerator(generator: ScGenerator): Unit = {

        for {
          forExpression <- generator.forStatement
          desugaredGenerator <- generator.desugared
        } {
          val sessionForDesugaredCode = new AnnotationSession(desugaredGenerator.analogMethodCall.getContainingFile)
          def delegateHolderFor(element: PsiElement) = new DelegateAnnotationHolder(element, holder, sessionForDesugaredCode) with ErrorIndication

          val followingEnumerators = generator.nextSiblings
            .takeWhile(!_.isInstanceOf[ScGenerator])
            .collect { case enum: ScEnumerator => enum}

          val foundUnresolvedSymbol = followingEnumerators
            .flatMap { enum => enum.desugared.map(enum -> _)}
            .exists {
              case (enum, desugaredEnum) =>
                val holder = delegateHolderFor(enum.enumeratorToken)
                desugaredEnum.callExpr.foreach(qualifierPart(_, holder))
                holder.hadError
            }

          // It doesn't make sense to look for errors down the function chain
          // if we were not able to resolve a previous call
          if (!foundUnresolvedSymbol) {
            var foundMonadicError = false

            // check the return type of the next generator
            // we check the next generator here with it's predecessor,
            // because we don't want to do the check if foundUnresolvedSymbol is true
            if (forExpression.isYield) {
              val nextGenOpt = generator.nextSiblings.collectFirst { case gen: ScGenerator => gen }
              for (nextGen <- nextGenOpt) {
                foundMonadicError = nextGen.desugared.exists {
                  nextDesugaredGen =>
                    val holder = delegateHolderFor(nextGen)
                    nextDesugaredGen.analogMethodCall.annotate(holder, typeAware)
                    holder.hadError
                }

                if (!foundMonadicError) {
                  val holder = delegateHolderFor(nextGen)
                  desugaredGenerator.callExpr.foreach(annotateReference(_, holder))
                  foundMonadicError = holder.hadError
                }
              }
            }

            if (!foundMonadicError) {
              desugaredGenerator.callExpr.foreach(qualifierPart(_, delegateHolderFor(generator.enumeratorToken)))
            }
          }
        }
      }

      override def visitGenerator(gen: ScGenerator) {
        checkGenerator(gen)
        gen.valKeyword match {
          case Some(valKeyword) =>
            val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("generator.val.keyword.removed"))
            annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
            annotation.registerFix(new RemoveValFromGeneratorIntentionAction(gen))
          case _ =>
        }
        super.visitGenerator(gen)
      }

      override def visitForExpression(expr: ScFor) {
        registerUsedImports(expr, ScalaPsiUtil.getExprImports(expr))
        super.visitForExpression(expr)
      }

      override def visitMethodCallExpression(call: ScMethodCall) {
        registerUsedImports(call, call.getImportsUsed)
        if (typeAware) annotateMethodInvocation(call, holder)
        super.visitMethodCallExpression(call)
      }

      override def visitInfixExpression(infix: ScInfixExpr): Unit = {
        if (typeAware) annotateMethodInvocation(infix, holder)
        super.visitInfixExpression(infix)
      }

      override def visitConstrBlock(constr: ScConstrBlock) {
        annotateAuxiliaryConstructor(constr, holder)
        super.visitConstrBlock(constr)
      }

      override def visitFunctionDefinition(fun: ScFunctionDefinition) {
        if (!compiled && !fun.isConstructor)
          annotateFunction(fun, holder, typeAware)
        super.visitFunctionDefinition(fun)
      }

      override def visitFunctionDeclaration(fun: ScFunctionDeclaration) {
        checkAbstractMemberPrivateModifier(fun, Seq(fun.nameId), holder)
        super.visitFunctionDeclaration(fun)
      }

      override def visitFunction(fun: ScFunction) {
        if (typeAware && !compiled && fun.getParent.isInstanceOf[ScTemplateBody]) {
          checkOverrideMethods(fun, holder, isInSources)
        }
        if (!fun.isConstructor) checkFunctionForVariance(fun, holder)
        super.visitFunction(fun)
      }

      override def visitTypeProjection(proj: ScTypeProjection) {
        referencePart(proj)
        visitTypeElement(proj)
      }

      override def visitUnderscoreExpression(under: ScUnderscoreSection) {
        checkUnboundUnderscore(under, holder)
        // TODO (otherwise there's no type conformance check)
        // super.visitUnderscoreExpression
      }

      private def referencePart(ref: ScReference, innerHolder: AnnotationHolder = holder) {
        if (typeAware) annotateReference(ref, innerHolder)
        qualifierPart(ref, innerHolder)
      }

      private def qualifierPart(ref: ScReference, innerHolder: AnnotationHolder): Unit = {
        ref.qualifier match {
          case None => checkNotQualifiedReferenceElement(ref, innerHolder)
          case Some(_) => checkQualifiedReferenceElement(ref, innerHolder)
        }
      }

      override def visitReference(ref: ScReference) {
        referencePart(ref)
        super.visitReference(ref)
      }

      override def visitConstructor(constr: ScConstructor) {
        if (typeAware) {
          ImplicitParametersAnnotator.annotate(constr, holder, typeAware)
          annotateConstructor(constr, holder)
        }
        super.visitConstructor(constr)
      }

      override def visitModifierList(modifierList: ScModifierList) {
        ModifierChecker.checkModifiers(modifierList, holder)
        super.visitModifierList(modifierList)
      }

      override def visitExistentialTypeElement(exist: ScExistentialTypeElement): Unit = {
        Stats.trigger(isInSources, FeatureKey.existentialType)
        super.visitExistentialTypeElement(exist)
      }

      override def visitTypeAlias(alias: ScTypeAlias) {
        if (typeAware && !compiled && alias.getParent.isInstanceOf[ScTemplateBody]) {
          checkOverrideTypes(alias, holder)
        }
        if(!compoundType(alias)) checkBoundsVariance(alias, holder, alias.nameId, alias, checkTypeDeclaredSameBracket = false)
        super.visitTypeAlias(alias)
      }

      override def visitVariable(varr: ScVariable) {
        if (typeAware && !compiled && (varr.getParent.isInstanceOf[ScTemplateBody] ||
                varr.getParent.isInstanceOf[ScEarlyDefinitions])) {
          checkOverrideVars(varr, holder, isInSources)
        }
        varr.typeElement match {
          case Some(typ) => checkBoundsVariance(varr, holder, typ, varr, checkTypeDeclaredSameBracket = false)
          case _ =>
        }
        if (!childHasAnnotation(varr.typeElement, "uncheckedVariance")) {
          checkValueAndVariableVariance(varr, Covariant, varr.declaredElements, holder)
          checkValueAndVariableVariance(varr, Contravariant, varr.declaredElements, holder)
        }
        super.visitVariable(varr)
      }

      override def visitValueDeclaration(v: ScValueDeclaration) {
        checkAbstractMemberPrivateModifier(v, v.declaredElements.map(_.nameId), holder)
        super.visitValueDeclaration(v)
      }

      override def visitValue(v: ScValue) {
        if (typeAware && !compiled && (v.getParent.isInstanceOf[ScTemplateBody] ||
                v.getParent.isInstanceOf[ScEarlyDefinitions])) {
          checkOverrideVals(v, holder, isInSources)
        }
        v.typeElement match {
          case Some(typ) => checkBoundsVariance(v, holder, typ, v, checkTypeDeclaredSameBracket = false)
          case _ =>
        }
        if (!childHasAnnotation(v.typeElement, "uncheckedVariance")) {
          checkValueAndVariableVariance(v, Covariant, v.declaredElements, holder)
        }
        super.visitValue(v)
      }

      override def visitClassParameter(parameter: ScClassParameter) {
        if (typeAware && !compiled) {
          checkOverrideClassParameters(parameter, holder)
        }
        checkClassParameterVariance(parameter, holder)
        super.visitClassParameter(parameter)
      }

      override def visitTemplateParents(tp: ScTemplateParents): Unit = {
        checkTemplateParentsVariance(tp, holder)
        super.visitTemplateParents(tp)
      }
    }
    annotateScope(element, holder)
    element.accept(visitor)

    element match {
      case templateDefinition: ScTemplateDefinition =>
        checkBoundsVariance(templateDefinition, holder, templateDefinition.nameId, templateDefinition.nameId, Covariant)

        ScalaAnnotator.AnnotatorParts.foreach {
          _.annotate(templateDefinition, holder, typeAware)
        }

        templateDefinition match {
          case cls: ScClass => CaseClassWithoutParamList.annotate(cls, holder, typeAware)
          case trt: ScTrait => TraitHasImplicitBound.annotate(trt, holder, typeAware)
          case _ =>
        }
      case _ =>
    }

    //todo: super[ControlFlowInspections].annotate(element, holder)
  }


  def isAdvancedHighlightingEnabled(element: PsiElement): Boolean =
    ScalaAnnotator.isAdvancedHighlightingEnabled(element)

  def checkBoundsVariance(toCheck: PsiElement, holder: AnnotationHolder, toHighlight: PsiElement, checkParentOf: PsiElement,
                          upperV: Variance = Covariant, checkTypeDeclaredSameBracket: Boolean = true, insideParameterized: Boolean = false) {
    toCheck match {
      case boundOwner: ScTypeBoundsOwner =>
        checkAndHighlightBounds(boundOwner.upperTypeElement, upperV)
        checkAndHighlightBounds(boundOwner.lowerTypeElement, -upperV)
      case _ =>
    }
    toCheck match {
      case paramOwner: ScTypeParametersOwner =>
        val inParameterized = if (paramOwner.isInstanceOf[ScTemplateDefinition]) false else true
        for (param <- paramOwner.typeParameters) {
          checkBoundsVariance(param, holder, param.nameId, checkParentOf, -upperV, insideParameterized = inParameterized)
        }
      case _ =>
    }

    def checkAndHighlightBounds(boundOption: Option[ScTypeElement], expectedVariance: Variance) {
      boundOption match {
        case Some(bound) if !childHasAnnotation(Some(bound), "uncheckedVariance") =>
          checkVariance(bound.calcType, expectedVariance, toHighlight, checkParentOf, holder, checkTypeDeclaredSameBracket, insideParameterized)
        case _ =>
      }
    }
  }



  private def checkNotQualifiedReferenceElement(refElement: ScReference, holder: AnnotationHolder) {
    refElement match {
      case _: ScInterpolatedStringPartReference =>
        return //do not inspect interpolated literal, it will be highlighted in other place
      case _ =>
    }

    def getFixes: Seq[IntentionAction] = {
      val classes = ScalaImportTypeFix.getTypesToImport(refElement, refElement.getProject)
      if (classes.length == 0) return Seq.empty
      Seq[IntentionAction](new ScalaImportTypeFix(classes, refElement))
    }

    val resolve = refElement.multiResolveScala(false)
    def processError(countError: Boolean, fixes: => Seq[IntentionAction]) {
      //todo remove when resolve of unqualified expression will be fully implemented
      if (refElement.getManager.isInProject(refElement) && resolve.length == 0 &&
              (fixes.nonEmpty || countError)) {
        val error = ScalaBundle.message("cannot.resolve", refElement.refName)
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        registerAddFixes(refElement, annotation, fixes: _*)
        annotation.registerFix(ReportHighlightingErrorQuickFix)
        registerCreateFromUsageFixesFor(refElement, annotation)
      }
    }

    if (refElement.isSoft) {
      return
    }


    val goodDoc = refElement.isInstanceOf[ScDocResolvableCodeReference] && resolve.length > 1
    if (resolve.length != 1 && !goodDoc) {
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

    if (isAdvancedHighlightingEnabled(refElement) && resolve.length != 1 && !goodDoc) {
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

  private def highlightImplicitMethod(expr: ScExpression, resolveResult: ScalaResolveResult, refElement: ScReference,
                                      fun: PsiNamedElement, holder: AnnotationHolder) {
    val typeTo = resolveResult.implicitType match {
      case Some(tp) => tp
      case _ => Any
    }
    highlightImplicitView(expr, fun, typeTo, refElement.nameId, holder)
  }

  private def highlightImplicitView(expr: ScExpression, fun: PsiNamedElement, typeTo: ScType,
                                    elementToHighlight: PsiElement, holder: AnnotationHolder) {
    if (ScalaProjectSettings.getInstance(elementToHighlight.getProject).isShowImplisitConversions) {
      val range = elementToHighlight.getTextRange
      val annotation: Annotation = holder.createInfoAnnotation(range, null)
      annotation.setTextAttributes(DefaultHighlighter.IMPLICIT_CONVERSIONS)
      annotation.setAfterEndOfLine(false)
    }
  }

  private def checkQualifiedReferenceElement(refElement: ScReference, holder: AnnotationHolder) {
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
    if (isAdvancedHighlightingEnabled(refElement) && resolveCount != 1) {
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

  private def checkUnboundUnderscore(under: ScUnderscoreSection, holder: AnnotationHolder) {
    if (under.getText == "_") {
      under.parentOfType(classOf[ScValueOrVariable], strict = false).foreach {
        case varDef @ ScVariableDefinition.expr(_) if varDef.expr.contains(under) =>
          if (varDef.containingClass == null) {
            val error = ScalaBundle.message("local.variables.must.be.initialized")
            holder.createErrorAnnotation(under, error)
          } else if (varDef.typeElement.isEmpty) {
            val error = ScalaBundle.message("unbound.placeholder.parameter")
            holder.createErrorAnnotation(under, error)
          } else if (varDef.typeElement.exists(_.isInstanceOf[ScLiteralTypeElement])) {
            holder.createErrorAnnotation(varDef.typeElement.get, ScalaBundle.message("default.init.prohibited.literal.types"))
          }
        case valDef @ ScPatternDefinition.expr(_) if valDef.expr.contains(under) =>
          holder.createErrorAnnotation(under, ScalaBundle.message("unbound.placeholder.parameter"))
        case _ =>
          // TODO SCL-2610 properly detect unbound placeholders, e.g. ( { _; (_: Int) } ) and report them.
          //  val error = ScalaBundle.message("unbound.placeholder.parameter")
          //  val annotation: Annotation = holder.createErrorAnnotation(under, error)
      }
    }
  }

  def childHasAnnotation(teOption: Option[PsiElement], annotation: String): Boolean = teOption match {
    case Some(te) => te.breadthFirst().exists {
      case annot: ScAnnotationExpr =>
        annot.constr.reference match {
          case Some(ref) => Option(ref.resolve()) match {
            case Some(res: PsiNamedElement) => res.getName == annotation
            case _ => false
          }
          case _ => false
        }
      case _ => false
    }
    case _ => false
  }

  private def checkFunctionForVariance(fun: ScFunction, holder: AnnotationHolder) {
    if (!modifierIsThis(fun) && !compoundType(fun)) { //if modifier contains [this] or if it is a compound type we do not highlight it
      checkBoundsVariance(fun, holder, fun.nameId, fun.getParent)
      if (!childHasAnnotation(fun.returnTypeElement, "uncheckedVariance")) {
        fun.returnType match {
          case Right(returnType) =>
            checkVariance(ScalaType.expandAliases(returnType).getOrElse(returnType), Covariant, fun.nameId,
              fun.getParent, holder)
          case _ =>
        }
      }
      for (parameter <- fun.parameters) {
        parameter.typeElement match {
          case Some(te) if !childHasAnnotation(Some(te), "uncheckedVariance") =>
            checkVariance(ScalaType.expandAliases(te.calcType).getOrElse(te.calcType), Contravariant,
              parameter.nameId, fun.getParent, holder)
          case _ =>
        }
      }
    }
  }

  private def checkTypeVariance(typeable: Typeable, variance: Variance, toHighlight: PsiElement, checkParentOf: PsiElement,
                                holder: AnnotationHolder): Unit = {
    typeable.`type`() match {
      case Right(tp) =>
        ScalaType.expandAliases(tp) match {
          case Right(newTp) => checkVariance(newTp, variance, toHighlight, checkParentOf, holder)
          case _ => checkVariance(tp, variance, toHighlight, checkParentOf, holder)
        }
      case _ =>
    }
  }

  def checkClassParameterVariance(toCheck: ScClassParameter, holder: AnnotationHolder): Unit = {
    if (toCheck.isVar && !modifierIsThis(toCheck) && !childHasAnnotation(Some(toCheck), "uncheckedVariance"))
      checkTypeVariance(toCheck, Contravariant, toCheck.nameId, toCheck, holder)
  }

  def checkTemplateParentsVariance(parents: ScTemplateParents, holder: AnnotationHolder): Unit = {
    for (typeElement <- parents.typeElements) {
      if (!childHasAnnotation(Some(typeElement), "uncheckedVariance") && !parents.parent.flatMap(_.parent).exists(_.isInstanceOf[ScNewTemplateDefinition]))
        checkTypeVariance(typeElement, Covariant, typeElement, parents, holder)
    }
  }

  def checkValueAndVariableVariance(toCheck: ScDeclaredElementsHolder, variance: Variance,
                                    declaredElements: Seq[Typeable with ScNamedElement], holder: AnnotationHolder) {
    if (!modifierIsThis(toCheck)) {
      for (element <- declaredElements) {
        checkTypeVariance(element, variance, element.nameId, toCheck, holder)
      }
    }
  }

  def modifierIsThis(toCheck: PsiElement): Boolean = {
    toCheck match {
      case modifierOwner: ScModifierListOwner =>
        Option(modifierOwner.getModifierList).flatMap(_.accessModifier).exists(_.isThis)
      case _ => false
    }
  }

  def compoundType(toCheck: PsiElement): Boolean = {
    toCheck.getParent.getParent match {
      case _: ScCompoundTypeElement => true
      case _ => false
    }
  }

  //fix for SCL-807
  private def checkVariance(typeParam: ScType, variance: Variance, toHighlight: PsiElement, checkParentOf: PsiElement,
                            holder: AnnotationHolder, checkIfTypeIsInSameBrackets: Boolean = false, insideParameterized: Boolean = false) = {

    def highlightVarianceError(elementV: Variance, positionV: Variance, name: String) = {
      if (positionV != elementV && elementV != Invariant) {
        val pos =
          if (toHighlight.isInstanceOf[ScVariable]) toHighlight.getText + "_="
          else toHighlight.getText
        val place = if (toHighlight.isInstanceOf[ScFunction]) "method" else "value"
        val elementVariance = elementV.name
        val posVariance = positionV.name
        val annotation = holder.createErrorAnnotation(toHighlight,
          ScalaBundle.message(s"$elementVariance.type.$posVariance.position.of.$place", name, typeParam.toString, pos))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
      }
    }

    def functionToSendIn(tp: ScType, v: Variance) = {
      tp match {
        case paramType: TypeParameterType =>
          paramType.psiTypeParameter match {
            case scTypeParam: ScTypeParam =>
              val compareTo = scTypeParam.owner
              val parentIt = checkParentOf.parents
              //if it's a function inside function we do not highlight it unless trait or class is defined inside this function
              parentIt.find(e => e == compareTo || e.isInstanceOf[ScFunction]) match {
                case Some(_: ScFunction) =>
                case _ =>
                  def findVariance: Variance = {
                    if (!checkIfTypeIsInSameBrackets) return v
                    if (PsiTreeUtil.isAncestor(scTypeParam.getParent, toHighlight, false))
                    //we do not highlight element if it was declared inside parameterized type.
                      if (!scTypeParam.getParent.getParent.isInstanceOf[ScTemplateDefinition]) return scTypeParam.variance
                      else return -v
                    if (toHighlight.getParent == scTypeParam.getParent.getParent) return -v
                    v
                  }
                  highlightVarianceError(scTypeParam.variance, findVariance, paramType.name)
              }
            case _ =>
          }
        case _ =>
      }
      ProcessSubtypes
    }
    typeParam.recursiveVarianceUpdate(variance)(functionToSendIn)
  }
}

object ScalaAnnotator {
  val ignoreHighlightingKey: Key[(Long, mutable.HashSet[TextRange])] = Key.create("ignore.highlighting.key")

  val usedImportsKey: Key[mutable.HashSet[ImportUsed]] = Key.create("used.imports.key")

  private val AnnotatorParts: Seq[TemplateDefinitionAnnotatorPart] = Seq(
    AbstractInstantiation,
    FinalClassInheritance,
    IllegalInheritance,
    ObjectCreationImpossible,
    MultipleInheritance,
    NeedsToBeAbstract,
    NeedsToBeMixin,
    NeedsToBeTrait,
    SealedClassInheritance,
    UndefinedMember
  )

  def forProject(implicit ctx: ProjectContext): ScalaAnnotator = new ScalaAnnotator {
    override implicit def projectContext: ProjectContext = ctx
  }

  // TODO place the method in HighlightingAdvisor
  def isAdvancedHighlightingEnabled(element: PsiElement): Boolean = {
    element.getContainingFile.toOption
      .exists { f =>
        isAdvancedHighlightingEnabled(f) && !isInIgnoredRange(element, f)
      }
  }

  def isAdvancedHighlightingEnabled(file: PsiFile): Boolean = file match {
    case scalaFile: ScalaFile =>
      HighlightingAdvisor.getInstance(file.getProject).enabled && !isLibrarySource(scalaFile) && !scalaFile.isInDottyModule
    case _: JavaDummyHolder =>
      HighlightingAdvisor.getInstance(file.getProject).enabled
    case _ => false
  }

  private def isInIgnoredRange(element: PsiElement, file: PsiFile): Boolean = {
    @CachedInUserData(file, file.getManager.getModificationTracker)
    def ignoredRanges(): Set[TextRange] = {
      val chars = file.charSequence
      val indexes = new ArrayBuffer[Int]
      var lastIndex = 0
      while (chars.indexOf("/*_*/", lastIndex) >= 0) {
        lastIndex = chars.indexOf("/*_*/", lastIndex) + 5
        indexes += lastIndex
      }
      if (indexes.isEmpty) return Set.empty

      if (indexes.length % 2 != 0) indexes += chars.length

      var res = Set.empty[TextRange]
      for (i <- indexes.indices by 2) {
        res += new TextRange(indexes(i), indexes(i + 1))
      }
      res
    }

    val ignored = ignoredRanges()
    if (ignored.isEmpty || element.isInstanceOf[PsiFile]) false
    else {
      val noCommentWhitespace = element.children.find {
        case _: PsiComment | _: PsiWhiteSpace => false
        case _ => true
      }
      val offset =
        noCommentWhitespace
          .map(_.getTextOffset)
          .getOrElse(element.getTextOffset)
      ignored.exists(_.contains(offset))
    }
  }

  private def isLibrarySource(file: ScalaFile): Boolean = {
    val vFile = file.getVirtualFile
    val index = ProjectFileIndex.SERVICE.getInstance(file.getProject)

    !file.isCompiled && vFile != null && index.isInLibrarySource(vFile)
  }

}
