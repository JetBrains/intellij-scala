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
  with VariableDefinitionAnnotator
  with TypedStatementAnnotator with PatternDefinitionAnnotator
  with ConstructorAnnotator
  with OverridingAnnotator with ValueClassAnnotator
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

      override def visitAnnotTypeElement(annot: ScAnnotTypeElement): Unit = {
        super.visitAnnotTypeElement(annot)
      }

      override def visitParameterizedTypeElement(parameterized: ScParameterizedTypeElement) {
        super.visitParameterizedTypeElement(parameterized)
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

      override def visitTypeElement(te: ScTypeElement) {
        checkLiteralTypesAllowed(te, holder)
        checkTypeElementForm(te, holder, typeAware)
        super.visitTypeElement(te)
      }


      override def visitLiteral(l: ScLiteral) {
        l match {
          case _ if l.getFirstChild.getNode.getElementType == ScalaTokenTypes.tINTEGER => // the literal is a tINTEGER
            checkIntegerLiteral(l, holder)
          case _ =>
        }

        if (MultilineStringUtil.isTooLongStringLiteral(l)) {
          holder.createErrorAnnotation(l, ScalaBundle.message("too.long.string.literal"))
        }

        super.visitLiteral(l)
      }

      override def visitAnnotation(annotation: ScAnnotation) {
        checkAnnotationType(annotation, holder)
        checkMetaAnnotation(annotation, holder)
        PrivateBeanProperty.annotate(annotation, holder)
        super.visitAnnotation(annotation)
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

      override def visitVariableDefinition(varr: ScVariableDefinition) {
        annotateVariableDefinition(varr, holder, typeAware)
        super.visitVariableDefinition(varr)
      }

      override def visitVariableDeclaration(varr: ScVariableDeclaration) {
        checkAbstractMemberPrivateModifier(varr, varr.declaredElements.map(_.nameId), holder)
        super.visitVariableDeclaration(varr)
      }

      override def visitTypedStmt(stmt: ScTypedExpression) {
        annotateTypedExpression(stmt, holder, typeAware)
        super.visitTypedStmt(stmt)
      }

      override def visitPatternDefinition(pat: ScPatternDefinition) {
        if (!compiled) {
          annotatePatternDefinition(pat, holder, typeAware)
        }
        super.visitPatternDefinition(pat)
      }

      override def visitPattern(pat: ScPattern) {
        super.visitPattern(pat)
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

      override def visitSelfInvocation(self: ScSelfInvocation) {
        super.visitSelfInvocation(self)
      }

      override def visitConstrBlock(constr: ScConstrBlock) {
        annotateAuxiliaryConstructor(constr, holder)
        super.visitConstrBlock(constr)
      }

      override def visitParameter(parameter: ScParameter) {
        super.visitParameter(parameter)
      }

      override def visitCatchBlock(c: ScCatchBlock) {
        super.visitCatchBlock(c)
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

      override def visitAssignmentStatement(stmt: ScAssignment) {
        super.visitAssignmentStatement(stmt)
      }

      override def visitTypeProjection(proj: ScTypeProjection) {
        referencePart(proj)
        visitTypeElement(proj)
      }

      override def visitUnderscoreExpression(under: ScUnderscoreSection) {
        checkUnboundUnderscore(under, holder)
      }

      private def referencePart(ref: ScReferenceElement, innerHolder: AnnotationHolder = holder) {
        if (typeAware) annotateReference(ref, innerHolder)
        qualifierPart(ref, innerHolder)
      }

      private def qualifierPart(ref: ScReferenceElement, innerHolder: AnnotationHolder): Unit = {
        ref.qualifier match {
          case None => checkNotQualifiedReferenceElement(ref, innerHolder)
          case Some(_) => checkQualifiedReferenceElement(ref, innerHolder)
        }
      }

      override def visitReference(ref: ScReferenceElement) {
        referencePart(ref)
        super.visitReference(ref)
      }

      override def visitImportExpr(expr: ScImportExpr) {
        checkImportExpr(expr, holder)
        super.visitImportExpr(expr)
      }

      override def visitReturnStatement(ret: ScReturn) {
        checkExplicitTypeForReturnStatement(ret, holder)
        super.visitReturnStatement(ret)
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

      override def visitParameters(parameters: ScParameters) {
        super.visitParameters(parameters)
      }

      override def visitTypeDefinition(typedef: ScTypeDefinition) {
        super.visitTypeDefinition(typedef)
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

      override def visitClass(cl: ScClass): Unit = {
        if (typeAware && ValueClassType.extendsAnyVal(cl)) annotateValueClass(cl, holder)
        super.visitClass(cl)
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

    element match {
      case sTypeParam: ScTypeBoundsOwner
        if !Option(PsiTreeUtil.getParentOfType(sTypeParam, classOf[ScTypeParamClause])).flatMap(_.parent).exists(_.isInstanceOf[ScFunction]) =>
        checkTypeParamBounds(sTypeParam, holder)
      case _ =>
    }
    //todo: super[ControlFlowInspections].annotate(element, holder)
  }

  private def checkMetaAnnotation(annotation: ScAnnotation, holder: AnnotationHolder): Unit = {
    import ScalaProjectSettings.ScalaMetaMode

    import scala.meta.intellij.psi._
    if (annotation.isMetaMacro) {
      if (!MetaExpansionsManager.isUpToDate(annotation)) {
        val warning = holder.createWarningAnnotation(annotation, ScalaBundle.message("scala.meta.recompile"))
        warning.registerFix(new RecompileAnnotationAction(annotation))
      }
      val result = annotation.parent.flatMap(_.parent) match {
        case Some(ah: ScAnnotationsHolder) => ah.metaExpand
        case _ => Right("")
      }
      val settings = ScalaProjectSettings.getInstance(annotation.getProject)
      result match {
        case Left(errorMsg) if settings.getScalaMetaMode == ScalaMetaMode.Enabled =>
          holder.createErrorAnnotation(annotation, ScalaBundle.message("scala.meta.expandfailed", errorMsg))
        case _ =>
      }
    }
  }

  def isAdvancedHighlightingEnabled(element: PsiElement): Boolean =
    ScalaAnnotator.isAdvancedHighlightingEnabled(element)

  private def checkTypeParamBounds(sTypeParam: ScTypeBoundsOwner, holder: AnnotationHolder) {
    for {
      lower <- sTypeParam.lowerBound.toOption
      upper <- sTypeParam.upperBound.toOption
      if !lower.conforms(upper)
      annotation = holder.createErrorAnnotation(sTypeParam,
        ScalaBundle.message("lower.bound.conform.to.upper", upper, lower))
    } annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
  }

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



  private def checkNotQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
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
        case e: ScStableCodeReferenceElement if e.getParent.isInstanceOf[ScInfixPattern] &&
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

  private def highlightImplicitMethod(expr: ScExpression, resolveResult: ScalaResolveResult, refElement: ScReferenceElement,
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

  private def checkQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
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

  private def checkAccessForReference(resolve: Array[ScalaResolveResult], refElement: ScReferenceElement, holder: AnnotationHolder) {
    if (resolve.length != 1 || refElement.isSoft || refElement.isInstanceOf[ScDocResolvableCodeReferenceImpl]) return
    resolve(0) match {
      case r if !r.isAccessible =>
        val error = "Symbol %s is inaccessible from this place".format(r.element.name)
        holder.createErrorAnnotation(refElement.nameId, error)
      //todo: add fixes
      case _ =>
    }
  }

  private def registerAddFixes(refElement: ScReferenceElement, annotation: Annotation, actions: IntentionAction*) {
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

  private def checkExplicitTypeForReturnStatement(statement: ScReturn, holder: AnnotationHolder): Unit = {
    val function = statement.method.getOrElse {
      val error = ScalaBundle.message("return.outside.method.definition")
      val annotation: Annotation = holder.createErrorAnnotation(statement.keyword, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      return
    }

    function.returnType match {
      case Right(tp) if function.hasAssign && !tp.equiv(Unit) =>
        val importUsed = statement.expr.toSet[ScExpression]
          .flatMap(_.getTypeAfterImplicitConversion().importsUsed)

        registerUsedImports(statement, importUsed)
      case _ =>
    }
  }

  private def checkImportExpr(impExpr: ScImportExpr, holder: AnnotationHolder) {
    if (impExpr.qualifier == null) {
      val annotation: Annotation = holder.createErrorAnnotation(impExpr.getTextRange,
        ScalaBundle.message("import.expr.should.be.qualified"))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }

  private def checkLiteralTypesAllowed(typeElement: ScTypeElement, holder: AnnotationHolder): Unit = {
    if (typeElement.isInstanceOf[ScLiteralTypeElement]) {
      import org.jetbrains.plugins.scala.project._
      if (!holder.getCurrentAnnotationSession.getFile.literalTypesEnabled)
        holder.createErrorAnnotation(typeElement, ScalaBundle.message("wrong.type.no.literal.types", typeElement.getText))
    }
  }

  private def checkTypeElementForm(typeElement: ScTypeElement, holder: AnnotationHolder, typeAware: Boolean) {
    //todo: check bounds conformance for parameterized type
    typeElement match {
      case simpleTypeElement: ScSimpleTypeElement =>
        checkAbsentTypeArgs(simpleTypeElement, holder)
      case _ =>
    }
  }

  private def checkAbsentTypeArgs(simpleTypeElement: ScSimpleTypeElement, holder: AnnotationHolder): Unit = {
    // Dirty hack(see SCL-12582): we shouldn't complain about missing type args since they will be added by a macro after expansion
    def isFreestyleAnnotated(ah: ScAnnotationsHolder): Boolean = {
      (ah.findAnnotationNoAliases("freestyle.free") != null) ||
        ah.findAnnotationNoAliases("freestyle.module") != null
    }
    def needTypeArgs: Boolean = {
      def noHigherKinds(owner: ScTypeParametersOwner) = !owner.typeParameters.exists(_.typeParameters.nonEmpty)

      val canHaveTypeArgs = simpleTypeElement.reference.map(_.resolve()).exists {
        case ah: ScAnnotationsHolder if isFreestyleAnnotated(ah) => false
        case c: PsiClass => c.hasTypeParameters
        case owner: ScTypeParametersOwner => owner.typeParameters.nonEmpty
        case _ => false
      }

      if (!canHaveTypeArgs) return false

      simpleTypeElement.getParent match {
        case ScParameterizedTypeElement(`simpleTypeElement`, _) => false
        case tp: ScTypeParam if tp.contextBoundTypeElement.contains(simpleTypeElement) => false
        case (_: ScTypeArgs) childOf (gc: ScGenericCall) =>
          gc.referencedExpr match {
            case ResolvesTo(f: ScFunction) => noHigherKinds(f)
            case _ => false
          }
        case (_: ScTypeArgs) childOf (parameterized: ScParameterizedTypeElement) =>
          parameterized.typeElement match {
            case ScSimpleTypeElement(Some(ResolvesTo(owner: ScTypeParametersOwner))) => noHigherKinds(owner)
            case ScSimpleTypeElement(Some(ResolvesTo(ScPrimaryConstructor.ofClass(c)))) => noHigherKinds(c)
            case _ => false
          }
        case infix: ScInfixTypeElement if infix.left == simpleTypeElement || infix.rightOption.contains(simpleTypeElement) =>
          infix.operation.resolve() match {
            case owner: ScTypeParametersOwner => noHigherKinds(owner)
            case _ => false
          }
        case _ => true
      }
    }

    if (needTypeArgs) {
      val annotation = holder.createErrorAnnotation(simpleTypeElement.getTextRange,
        ScalaBundle.message("type.takes.type.parameters", simpleTypeElement.getText))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }

  private def checkAnnotationType(annotation: ScAnnotation, holder: AnnotationHolder) {
    //todo: check annotation is inheritor for class scala.Annotation
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

  //fix for SCL-7176
  private def checkAbstractMemberPrivateModifier(element: PsiElement, toHighlight: Seq[PsiElement], holder: AnnotationHolder) {
    element match {
      case fun: ScFunctionDeclaration if fun.isNative =>
      case modOwner: ScModifierListOwner =>
        modOwner.getModifierList.accessModifier match {
          case Some(am) if am.isUnqualifiedPrivateOrThis =>
            for (e <- toHighlight) {
              val annotation = holder.createErrorAnnotation(e, ScalaBundle.message("abstract.member.not.have.private.modifier"))
              annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
            }
          case _ =>
        }
      case _ =>
    }
  }

  private def checkIntegerLiteral(literal: ScLiteral, holder: AnnotationHolder) {
    val child = literal.getFirstChild.getNode
    val text = literal.getText
    val endsWithL = child.getText.endsWith("l") || child.getText.endsWith("L")
    val textWithoutL = if (endsWithL) text.substring(0, text.length - 1) else text
    val parent = literal.getParent
    val scalaVersion = literal.scalaLanguageLevel
    val isNegative = parent match {
      // only "-1234" is negative, "- 1234" should be considered as positive 1234
      case prefixExpr: ScPrefixExpr if prefixExpr.getChildren.length == 2 && prefixExpr.getFirstChild.getText == "-" => true
      case _ => false
    }
    val (number, base) = textWithoutL match {
      case t if t.startsWith("0x") || t.startsWith("0X") => (t.substring(2), 16)
      case t if t.startsWith("0") && t.length >= 2 => (t.substring(1), 8)
      case t => (t, 10)
    }

    // parse integer literal. the return is (Option(value), statusCode)
    // the Option(value) will be the real integer represented by the literal, if it cannot fit in Long, It's None
    // there is 3 value for statusCode:
    // 0 -> the literal can fit in Int
    // 1 -> the literal can fit in Long
    // 2 -> the literal cannot fit in Long
    def parseIntegerNumber(text: String, isNegative: Boolean): (Option[Long], Byte) = {
      var value = 0l
      val divider = if (base == 10) 1 else 2
      var statusCode: Byte = 0
      val limit = java.lang.Long.MAX_VALUE
      val intLimit = java.lang.Integer.MAX_VALUE
      var i = 0
      for (d <- number.map(_.asDigit)) {
        if (value > intLimit ||
            intLimit / (base / divider) < value ||
            intLimit - (d / divider) < value * (base / divider) &&
            // This checks for -2147483648, value is 214748364, base is 10, d is 8. This check returns false.
            // base 8 and 16 won't have this check because the divider is 2        .
            !(isNegative && intLimit == value * base - 1 + d)) {
          statusCode = 1
        }
        if (value < 0 ||
            limit / (base / divider) < value ||
            limit - (d / divider) < value * (base / divider) &&
            // This checks for Long.MinValue, same as the the previous Int.MinValue check.
            !(isNegative && limit == value * base - 1 + d)) {
          return (None, 2)
        }
        value = value * base + d
        i += 1
      }
      value = if (isNegative) -value else value
      if (statusCode == 0) (Some(value.toInt), 0) else (Some(value), statusCode)
    }

    if (base == 8) {
      val convertFix = new ConvertOctalToHexFix(literal)
      scalaVersion match {
        case Some(ScalaLanguageLevel.Scala_2_10) =>
          val deprecatedMeaasge = "Octal number is deprecated in Scala-2.10 and will be removed in Scala-2.11"
          val annotation = holder.createWarningAnnotation(literal, deprecatedMeaasge)
          annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
          annotation.registerFix(convertFix)
        case Some(version) if version >= ScalaLanguageLevel.Scala_2_11 =>
          val error = "Octal number is removed in Scala-2.11 and after"
          val annotation = holder.createErrorAnnotation(literal, error)
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
          annotation.registerFix(convertFix)
          return
        case _ =>
      }
    }
    val (_, status) = parseIntegerNumber(number, isNegative)
    if (status == 2) { // the Integer number is out of range even for Long
      val error = "Integer number is out of range even for type Long"
      holder.createErrorAnnotation(literal, error)
    } else {
      if (status == 1 && !endsWithL) {
        val error = "Integer number is out of range for type Int"
        val annotation = if (isNegative) holder.createErrorAnnotation(parent, error) else holder.createErrorAnnotation(literal, error)

        val Long = literal.projectContext.stdTypes.Long
        val conformsToTypeList = Seq(Long) ++ createTypeFromText("_root_.scala.math.BigInt", literal.getContext, literal)
        val shouldRegisterFix = (if (isNegative) parent.asInstanceOf[ScPrefixExpr] else literal).expectedType().forall { x =>
          conformsToTypeList.exists(_.weakConforms(x))
        }

        if (shouldRegisterFix) {
          val addLtoLongFix: AddLToLongLiteralFix = new AddLToLongLiteralFix(literal)
          annotation.registerFix(addLtoLongFix)
        }
      }
    }
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
