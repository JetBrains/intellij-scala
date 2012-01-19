package org.jetbrains.plugins.scala
package annotator

import collection.mutable.HashSet
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.util.PsiTreeUtil
import controlFlow.ControlFlowInspections
import createFromUsage._
import highlighter.AnnotatorHighlighter
import importsTracker._
import lang.psi.api.statements._
import lang.psi.api.toplevel.typedef._
import lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.lang.annotation._

import lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.annotator.intention._
import params.{ScParameter, ScParameters, ScClassParameter}
import patterns.ScInfixPattern
import processor.MethodResolveProcessor
import quickfix.modifiers.{RemoveModifierQuickFix, AddModifierQuickFix}
import modifiers.ModifierChecker
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import quickfix.{ChangeTypeFix, ReportHighlightingErrorQuickFix}
import template._
import scala.Some
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import java.awt.{Font, Color}
import components.HighlightingAdvisor
import org.jetbrains.plugins.scala.extensions._
import collection.{Seq, Set}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ReadValueUsed, WriteValueUsed, ValueUsed, ImportUsed}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiManager, ScalaPsiElementFactory}
import result.{TypingContext, TypeResult, Success}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import types.{ScTypeProjection, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement, ScModifierListOwner, ScTypeBoundsOwner}

/**
 *    User: Alexander Podkhalyuzin
 *    Date: 23.06.2008
 */
class ScalaAnnotator extends Annotator with FunctionAnnotator with ScopeAnnotator
        with ParametersAnnotator with ApplicationAnnotator
        with AssignmentAnnotator with VariableDefinitionAnnotator
        with TypedStatementAnnotator with PatternDefinitionAnnotator
        with ControlFlowInspections with ConstructorAnnotator
        with DumbAware {

  override def annotate(element: PsiElement, holder: AnnotationHolder) {
    val typeAware = isAdvancedHighlightingEnabled(element)

    val compiled = element.getContainingFile match {
      case file: ScalaFile => file.isCompiled
      case _ => false
    }

    val visitor = new ScalaElementVisitor {
      private def expressionPart(expr: ScExpression) {
        if (!compiled) {
          checkExpressionType(expr, holder, typeAware)
          checkExpressionImplicitParameters(expr, holder)
          ByNameParameter.annotate(expr, holder, typeAware)
        }

        if (isAdvancedHighlightingEnabled(element) && settings(element).SHOW_IMPLICIT_CONVERSIONS) {
          expr.getTypeExt(TypingContext.empty) match {
            case ExpressionTypeResult(Success(t, _), _, Some(implicitFunction)) =>
              highlightImplicitView(expr, implicitFunction, t, expr, holder)
            case _ =>
          }
        }
      }

      override def visitExpression(expr: ScExpression) {
        expressionPart(expr)
        super.visitExpression(expr)
      }

      override def visitReferenceExpression(ref: ScReferenceExpression) {
        referencePart(ref)
        visitExpression(ref)
      }

      override def visitTypeElement(te: ScTypeElement) {
        checkTypeElementForm(te, holder)
        super.visitTypeElement(te)
      }

      override def visitAnnotation(annotation: ScAnnotation) {
        checkAnnotationType(annotation, holder)
        super.visitAnnotation(annotation)
      }

      override def visitForExpression(expr: ScForStatement) {
        checkForStmtUsedTypes(expr, holder)
        super.visitForExpression(expr)
      }

      override def visitVariableDefinition(varr: ScVariableDefinition) {
        annotateVariableDefinition(varr, holder, typeAware)
        super.visitVariableDefinition(varr)
      }

      override def visitTypedStmt(stmt: ScTypedStmt) {
        annotateTypedStatement(stmt, holder, typeAware)
        super.visitTypedStmt(stmt)
      }

      override def visitPatternDefinition(pat: ScPatternDefinition) {
        if (!compiled) {
          annotatePatternDefinition(pat, holder, typeAware)
        }
        super.visitPatternDefinition(pat)
      }

      override def visitMethodCallExpression(call: ScMethodCall) {
        checkMethodCallImplicitConversion(call, holder)
        annotateMethodCall(call, holder)
        super.visitMethodCallExpression(call)
      }

      override def visitSelfInvocation(self: ScSelfInvocation) {
        checkSelfInvocation(self, holder)
        super.visitSelfInvocation(self)
      }

      override def visitConstrBlock(constr: ScConstrBlock) {
        annotateAuxiliaryConstructor(constr, holder)
        super.visitConstrBlock(constr)
      }

      override def visitParameter(parameter: ScParameter) {
        annotateParameter(parameter, holder)
        super.visitParameter(parameter)
      }

      override def visitCatchBlock(c: ScCatchBlock) {
        checkCatchBlockGeneralizedRule(c, holder, typeAware)
        super.visitCatchBlock(c)
      }

      override def visitFunctionDefinition(fun: ScFunctionDefinition) {
        if (!compiled && !fun.isConstructor)
          annotateFunction(fun, holder, typeAware)
        super.visitFunctionDefinition(fun)
      }

      override def visitFunction(fun: ScFunction) {
        if (typeAware && !compiled && fun.getParent.isInstanceOf[ScTemplateBody]) {
          checkOverrideMethods(fun, holder)
        }
        super.visitFunction(fun)
      }

      override def visitAssignmentStatement(stmt: ScAssignStmt) {
        annotateAssignment(stmt, holder, typeAware)
        super.visitAssignmentStatement(stmt)
      }

      override def visitTypeProjection(proj: ScTypeProjection) {
        referencePart(proj)
        visitTypeElement(proj)
      }

      private def referencePart(ref: ScReferenceElement) {
        if(typeAware) annotateReference(ref, holder)
        ref.qualifier match {
          case None => checkNotQualifiedReferenceElement(ref, holder)
          case Some(_) => checkQualifiedReferenceElement(ref, holder)
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

      override def visitReturnStatement(ret: ScReturnStmt) {
        checkExplicitTypeForReturnStatement(ret, holder)
        super.visitReturnStatement(ret)
      }

      override def visitConstructor(constr: ScConstructor) {
        if(typeAware) annotateConstructor(constr, holder)
        super.visitConstructor(constr)
      }

      override def visitModifierList(modifierList: ScModifierList) {
        ModifierChecker.checkModifiers(modifierList, holder)
        super.visitModifierList(modifierList)
      }

      override def visitParameters(parameters: ScParameters) {
        annotateParameters(parameters, holder)
        super.visitParameters(parameters)
      }

      override def visitTypeDefintion(typedef: ScTypeDefinition) {
        if (typeAware && !compiled && typedef.getParent.isInstanceOf[ScTemplateBody]) {
          checkOverrideTypes(typedef, holder)
        }
        super.visitTypeDefintion(typedef)
      }

      override def visitTypeAlias(alias: ScTypeAlias) {
        if (typeAware && !compiled && alias.getParent.isInstanceOf[ScTemplateBody]) {
          checkOverrideTypes(alias, holder)
        }
        super.visitTypeAlias(alias)
      }

      override def visitVariable(varr: ScVariable) {
        if (typeAware && !compiled && (varr.getParent.isInstanceOf[ScTemplateBody] ||
          varr.getParent.isInstanceOf[ScEarlyDefinitions])) {
          checkOverrideVars(varr, holder)
        }
        super.visitVariable(varr)
      }

      override def visitValue(v: ScValue) {
        if (typeAware && !compiled && (v.getParent.isInstanceOf[ScTemplateBody] ||
          v.getParent.isInstanceOf[ScEarlyDefinitions])) {
          checkOverrideVals(v, holder)
        }
        super.visitValue(v)
      }

      override def visitClassParameter(parameter: ScClassParameter) {
        if (typeAware && !compiled) {
          checkOverrideClassParameters(parameter, holder)
        }
        super.visitClassParameter(parameter)
      }
    }
    annotateScope(element, holder)
    element.accept(visitor)
    AnnotatorHighlighter.highlightElement(element, holder)

    if (element.isInstanceOf[ScTemplateDefinition]) {
      val templateDefinition = element.asInstanceOf[ScTemplateDefinition]
      val tdParts = Seq(AbstractInstantiation, FinalClassInheritance, IllegalInheritance, ObjectCreationImpossible,
        MultipleInheritance, NeedsToBeAbstract, NeedsToBeTrait, SealedClassInheritance, UndefinedMember)
      tdParts.foreach(_.annotate(templateDefinition, holder, typeAware))
      templateDefinition match {
        case cls: ScClass =>
          val clsParts = Seq(CaseClassWithoutParamList, HasImplicitParamAndBound)
          clsParts.foreach(_.annotate(cls, holder, typeAware))
        case trt: ScTrait =>
          val traitParts = Seq(TraitHasImplicitBound)
          traitParts.foreach(_.annotate(trt, holder, typeAware))
        case _ =>
      }
    }

    if (element.isInstanceOf[ScTypeBoundsOwner]) {
      val sTypeParam = element.asInstanceOf[ScTypeBoundsOwner]
      checkTypeParamBounds(sTypeParam, holder)
    }
    //todo: super[ControlFlowInspections].annotate(element, holder)
  }

  private def settings(element: PsiElement): ScalaCodeStyleSettings = {
    CodeStyleSettingsManager.getSettings(element.getProject)
            .getCustomSettings(classOf[ScalaCodeStyleSettings])
  }

  def isAdvancedHighlightingEnabled(element: PsiElement): Boolean = {
    HighlightingAdvisor.getInstance(element.getProject).enabled
  }

  def checkCatchBlockGeneralizedRule(block: ScCatchBlock, holder: AnnotationHolder, typeAware: Boolean) {
    block.expression match {
      case Some(expr) =>
        val tp = expr.getType(TypingContext.empty).getOrAny
        val throwable = ScalaPsiManager.instance(expr.getProject).getCachedClass(expr.getResolveScope, "java.lang.Throwable")
        if (throwable == null) return
        val throwableType = ScDesignatorType(throwable)
        def checkMember(memberName: String, checkReturnTypeIsBoolean: Boolean) {
          val processor = new MethodResolveProcessor(expr, memberName, List(Seq(new Compatibility.Expression(throwableType))),
            Seq.empty, Seq.empty)
          processor.processType(tp, expr)
          val candidates = processor.candidates
          if (candidates.length != 1) {
            val error = ScalaBundle.message("method.is.not.member", memberName, ScType.presentableText(tp))
            val annotation = holder.createErrorAnnotation(expr, error)
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
          } else if (checkReturnTypeIsBoolean) {
            def error() {
              val error = ScalaBundle.message("expected.type.boolean", memberName)
              val annotation = holder.createErrorAnnotation(expr, error)
              annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
            }
            candidates(0) match {
              case ScalaResolveResult(fun: ScFunction, subst) =>
                if (fun.returnType.isEmpty || !Equivalence.equiv(subst.subst(fun.returnType.get), Boolean)) {
                  error()
                }
              case _ => error()
            }
          } else {
            block.getContext match {
              case t: ScTryStmt =>
                t.expectedTypeEx(false) match {
                  case Some((tp: ScType, _)) if tp equiv Unit => //do nothing
                  case Some((tp: ScType, typeElement)) => {
                    import org.jetbrains.plugins.scala.lang.psi.types._
                    val returnType = candidates(0) match {
                      case ScalaResolveResult(fun: ScFunction, subst) => fun.returnType.map(subst.subst(_))
                      case _ => return
                    }
                    val expectedType = Success(tp, None)
                    val conformance = ScalaAnnotator.smartCheckConformance(expectedType, returnType)
                    if (!conformance) {
                      if (typeAware) {
                        val error = ScalaBundle.message("expr.type.does.not.conform.expected.type",
                          ScType.presentableText(returnType.getOrNothing), ScType.presentableText(expectedType.get))
                        val annotation: Annotation = holder.createErrorAnnotation(expr, error)
                        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                        typeElement match {
                          case Some(te) => annotation.registerFix(new ChangeTypeFix(te, returnType.getOrNothing))
                          case None =>
                        }
                      }
                    }
                  }
                  case _ => //do nothing
                }
              case _ =>
            }
          }
        }
        checkMember("isDefinedAt", true)
        checkMember("apply", false)  
      case _ =>
    }
  }

  private def checkTypeParamBounds(sTypeParam: ScTypeBoundsOwner, holder: AnnotationHolder) {}

  private def registerUsedElement(element: PsiElement, resolveResult: ScalaResolveResult,
                                   checkWrite: Boolean) {
    val named = resolveResult.getElement
    val file = element.getContainingFile
    if (named.isValid && named.getContainingFile == file) {
      val value: ValueUsed = element match {
        case ref: ScReferenceExpression if checkWrite &&
          ScalaPsiUtil.isPossiblyAssignment(ref.asInstanceOf[PsiElement]) => WriteValueUsed(named)
        case _ => ReadValueUsed(named)
      }
      val holder = ScalaRefCountHolder.getInstance(file)
      holder.registerValueUsed(value)
      // For use of unapply method, see SCL-3463
      resolveResult.parentElement.foreach(parent => holder.registerValueUsed(ReadValueUsed(parent)))
    }
  }

  private def checkNotQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    def getFix: Seq[IntentionAction] = {
      val classes = ScalaImportClassFix.getClasses(refElement, refElement.getProject)
      if (classes.length == 0) return Seq.empty
      Seq[IntentionAction](new ScalaImportClassFix(classes, refElement))
    }

    val resolve: Array[ResolveResult] = refElement.multiResolve(false)
    if (refElement.isInstanceOf[ScDocResolvableCodeReference] && resolve.length > 1) return
    def processError(countError: Boolean, fixes: => Seq[IntentionAction]) {
      //todo remove when resolve of unqualified expression will be fully implemented
      if (refElement.getManager.isInProject(refElement) && resolve.length == 0 &&
              (fixes.length > 0 || countError)) {
        val error = ScalaBundle.message("cannot.resolve", refElement.refName)
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        registerAddImportFix(refElement, annotation, fixes: _*)
        annotation.registerFix(ReportHighlightingErrorQuickFix)
        registerCreateFromUsageFixesFor(refElement, annotation)
      }
    }

    if (refElement.isSoft) {
      return
    }


    if (resolve.length != 1) {
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
                e.getParent.asInstanceOf[ScPrefixExpr].operation == e => //todo: this is hide !(Not Boolean)
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
                e.getParent.asInstanceOf[ScInfixExpr].operation == e => //todo: this is hide A op B
        case e: ScReferenceExpression => processError(false, getFix)
        case e: ScStableCodeReferenceElement if e.getParent.isInstanceOf[ScInfixPattern] &&
                e.getParent.asInstanceOf[ScInfixPattern].refernece == e => //todo: this is hide A op B in patterns
        case _ => refElement.getParent match {
          case s: ScImportSelector if resolve.length > 0 =>
          case _ => processError(true, getFix)
        }
      }
    }
    else {
      AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
    }
    for {
      result <- resolve if result.isInstanceOf[ScalaResolveResult]
      scalaResult = result.asInstanceOf[ScalaResolveResult]
    } {
      registerUsedImports(refElement, scalaResult)
      registerUsedElement(refElement, scalaResult, true)
    }

    checkAccessForReference(resolve, refElement, holder)
    checkForwardReference(resolve, refElement, holder)

    if (settings(refElement).SHOW_IMPLICIT_CONVERSIONS && resolve.length == 1) {
      val resolveResult = resolve(0).asInstanceOf[ScalaResolveResult]
      refElement match {
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScPrefixExpr] &&
                e.getParent.asInstanceOf[ScPrefixExpr].operation == e => {
          resolveResult.implicitFunction match {
            case Some(fun) => {
              val pref = e.getParent.asInstanceOf[ScPrefixExpr]
              val expr = pref.operand
              highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
            }
            case _ =>
          }
        }
        case e: ScReferenceExpression if e.getParent.isInstanceOf[ScInfixExpr] &&
                e.getParent.asInstanceOf[ScInfixExpr].operation == e => {
          resolveResult.implicitFunction match {
            case Some(fun) => {
              val inf = e.getParent.asInstanceOf[ScInfixExpr]
              val expr = if (inf.isLeftAssoc) inf.rOp else inf.lOp
              highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
            }
            case _ =>
          }
        }
        case _ =>
      }
    }

    if (isAdvancedHighlightingEnabled(refElement) && resolve.length != 1) {
      refElement.getParent match {
        case s: ScImportSelector if resolve.length > 0 => return
        case mc: ScMethodCall =>
          val refWithoutArgs = ScalaPsiElementFactory.createReferenceFromText(refElement.getText, mc.getContext, mc)
          if (refWithoutArgs.multiResolve(false).nonEmpty) {
            // We can't resolve the method call A(arg1, arg2), but we can resolve A. Highlight this differently.
            val error = ScalaBundle.message("cannot.resolve.apply.method", refElement.refName)
            val annotation = holder.createErrorAnnotation(refElement.nameId, error)
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
            annotation.registerFix(ReportHighlightingErrorQuickFix)
            annotation.registerFix(new CreateApplyQuickFix(refWithoutArgs, mc))
            return
          }
        case _ =>
      }

      val error = ScalaBundle.message("cannot.resolve", refElement.refName)
      val annotation = holder.createErrorAnnotation(refElement.nameId, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      annotation.registerFix(ReportHighlightingErrorQuickFix)
      registerCreateFromUsageFixesFor(refElement, annotation)
    }
  }

  private def registerCreateFromUsageFixesFor(ref: ScReferenceElement, annotation: Annotation) {
      ref match {
        case Both(exp: ScReferenceExpression, Parent(_: ScMethodCall)) =>
          annotation.registerFix(new CreateMethodQuickFix(exp))
        case Both(exp: ScReferenceExpression, Parent(infix: ScInfixExpr)) if infix.operation == exp =>
          annotation.registerFix(new CreateMethodQuickFix(exp))
        case exp: ScReferenceExpression =>
          annotation.registerFix(new CreateParameterlessMethodQuickFix(exp))
          annotation.registerFix(new CreateValueQuickFix(exp))
          annotation.registerFix(new CreateVariableQuickFix(exp))
        case _ =>
      }
    }

  private def highlightImplicitMethod(expr: ScExpression, resolveResult: ScalaResolveResult, refElement: ScReferenceElement,
                              fun: PsiNamedElement, holder: AnnotationHolder) {
    import org.jetbrains.plugins.scala.lang.psi.types.Any

    val typeTo = resolveResult.implicitType match {case Some(tp) => tp case _ => Any}
    highlightImplicitView(expr, fun, typeTo, refElement.nameId, holder)
  }

  private def highlightImplicitView(expr: ScExpression, fun: PsiNamedElement, typeTo: ScType,
                                    elementToHighlight: PsiElement, holder: AnnotationHolder) {
    val range = elementToHighlight.getTextRange
    val annotation: Annotation = holder.createInfoAnnotation(range, null)
    val attributes = new TextAttributes(null, null, Color.LIGHT_GRAY, EffectType.LINE_UNDERSCORE, Font.PLAIN)
    annotation.setEnforcedTextAttributes(attributes)
    annotation.setAfterEndOfLine(false)
  }

  private def checkSelfInvocation(self: ScSelfInvocation, holder: AnnotationHolder) {
    self.bind match {
      case Some(elem) =>
      case None => {
        if (isAdvancedHighlightingEnabled(self)) {
          val annotation: Annotation = holder.createErrorAnnotation(self.thisElement,
            "Cannot find constructor for this call")
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        }
      }
    }
  }

  private def checkQualifiedReferenceElement(refElement: ScReferenceElement, holder: AnnotationHolder) {
    AnnotatorHighlighter.highlightReferenceElement(refElement, holder)
    var resolve: Array[ResolveResult] = null
    resolve = refElement.multiResolve(false)
    for (result <- resolve if result.isInstanceOf[ScalaResolveResult];
         scalaResult = result.asInstanceOf[ScalaResolveResult]) {
      registerUsedImports(refElement, scalaResult)
      registerUsedElement(refElement, scalaResult, true)
    }
    checkAccessForReference(resolve, refElement, holder)
    if (refElement.isInstanceOf[ScExpression] &&
            settings(refElement).SHOW_IMPLICIT_CONVERSIONS && resolve.length == 1) {
      val resolveResult = resolve(0).asInstanceOf[ScalaResolveResult]
      resolveResult.implicitFunction match {
        case Some(fun) => {
          val qualifier = refElement.qualifier.get
          val expr = qualifier.asInstanceOf[ScExpression]
          highlightImplicitMethod(expr, resolveResult, refElement, fun, holder)
        }
        case _ =>
      }
    }

    if (refElement.isInstanceOf[ScDocResolvableCodeReference] && resolve.length > 0 || refElement.isSoft) return
    if (isAdvancedHighlightingEnabled(refElement) && resolve.length != 1) {
      refElement.getParent match {
        case _: ScImportSelector | _: ScImportExpr if resolve.length > 0 => return 
        case _ =>
      }
      val error = ScalaBundle.message("cannot.resolve", refElement.refName)
      val annotation = holder.createErrorAnnotation(refElement.nameId, error)
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      annotation.registerFix(ReportHighlightingErrorQuickFix)
      registerCreateFromUsageFixesFor(refElement, annotation)
    }
  }

  private def checkForwardReference(resolve: Array[ResolveResult], refElement: ScReferenceElement, holder: AnnotationHolder) {
    //todo: add check if it's legal to use forward reference
  }

  private def checkAccessForReference(resolve: Array[ResolveResult], refElement: ScReferenceElement, holder: AnnotationHolder) {
    if (resolve.length != 1 || refElement.isSoft) return
    resolve(0) match {
      case r: ScalaResolveResult if !r.isAccessible =>
        val error = "Symbol %s is inaccessible from this place".format(r.element.getName)
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      //todo: add fixes
      case _ =>
    }
  }

  private def registerAddImportFix(refElement: ScReferenceElement, annotation: Annotation, actions: IntentionAction*) {
    for (action <- actions) {
      annotation.registerFix(action)
    }
  }

  private def registerUsedImports(element: PsiElement, result: ScalaResolveResult) {
    ImportTracker.getInstance(element.getProject).
            registerUsedImports(element.getContainingFile.asInstanceOf[ScalaFile], result.importsUsed)
  }

  private def isConcreteElement(element: PsiElement): Boolean = {
    element match {
      case _: ScFunctionDefinition => true
      case f: ScFunctionDeclaration if f.isNative => true
      case _: ScFunctionDeclaration => false
      case _: ScFun => true
      case method: PsiMethod if !method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.isConstructor => true
      case method: PsiMethod if method.hasModifierProperty("native") => true
      case _: ScPatternDefinition => true
      case _: ScVariableDefinition => true
      case _: ScClassParameter => true
      case _: ScTypeDefinition => true
      case _: ScTypeAliasDefinition => true
      case _ => false
    }
  }

  private def checkOverrideMethods(method: ScFunction, holder: AnnotationHolder) {
    def isConcrete(signature: Signature): Boolean =
      if (signature.namedElement != None) {
        val element = ScalaPsiUtil.nameContext(signature.namedElement.get)
        isConcreteElement(element)
      } else false
    checkOverrideMembers(method, method, method.superSignatures, isConcrete, "Method", holder)
  }

  private def checkOverrideVals(v: ScValue, holder: AnnotationHolder) {
    def isConcrete(signature: Signature): Boolean =
      if (signature.namedElement != None) {
        val element = ScalaPsiUtil.nameContext(signature.namedElement.get)
        isConcreteElement(element)
      } else false
    
    v.declaredElements.foreach(td => {
      checkOverrideMembers(td, v, ScalaPsiUtil.superValsSignatures(td), isConcrete, "Value", holder)
    })
  }

  private def checkOverrideVars(v: ScVariable, holder: AnnotationHolder) {
    def isConcrete(signature: Signature): Boolean =
      if (signature.namedElement != None) {
        val element = ScalaPsiUtil.nameContext(signature.namedElement.get)
        isConcreteElement(element)
      } else false

    v.declaredElements.foreach(td => {
      checkOverrideMembers(td, v, ScalaPsiUtil.superValsSignatures(td), isConcrete, "Variable", holder)
    })
  }

  private def checkOverrideClassParameters(v: ScClassParameter, holder: AnnotationHolder) {
    def isConcrete(signature: Signature): Boolean =
      if (signature.namedElement != None) {
        val element = ScalaPsiUtil.nameContext(signature.namedElement.get)
        isConcreteElement(element)
      } else false

    checkOverrideMembers(v, v, ScalaPsiUtil.superValsSignatures(v), isConcrete, "Parameter", holder)
  }

  private def checkOverrideTypes(tp: ScNamedElement with ScModifierListOwner, holder: AnnotationHolder) {
    tp match {
      case c: ScTypeDefinition =>
      case a: ScTypeAlias =>
      case _ => return
    }
    checkOverrideMembers(tp, tp, ScalaPsiUtil.superTypeMembers(tp), isConcreteElement, "Type", holder)
  }
  private def checkOverrideMembers[T <: ScNamedElement, Res](member: T, 
                                                             owner: ScModifierListOwner,
                                                             superSignatures: Seq[Res], 
                                                             isConcrete: Res => Boolean,
                                                             memberType: String,
                                                             holder: AnnotationHolder) {
    if (superSignatures.length == 0) {
      if (owner.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(member.nameId.getTextRange,
          ScalaBundle.message("member.overrides.nothing", memberType, member.getName))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        annotation.registerFix(new RemoveModifierQuickFix(owner, "override"))
      }
    } else {
      var isConcretes = false
      for (signature <- superSignatures if !isConcretes && isConcrete(signature)) isConcretes = true
      if (isConcretes && !owner.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(member.nameId.getTextRange,
          ScalaBundle.message("member.needs.override.modifier", memberType, member.getName))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        annotation.registerFix(new AddModifierQuickFix(owner, "override"))
      }
    }
  }

  private def showImplicitUsageAnnotation(exprText: String, typeFrom: ScType, typeTo: ScType, fun: ScFunctionDefinition,
                                          range: TextRange, holder: AnnotationHolder, effectType: EffectType,
                                          color: Color) {
    val tooltip = ScalaBundle.message("implicit.usage.tooltip", fun.getName,
      ScType.presentableText(typeFrom),
      ScType.presentableText(typeTo))
    val message = ScalaBundle.message("implicit.usage.message", fun.getName,
      ScType.presentableText(typeFrom),
      ScType.presentableText(typeTo))
    val annotation: Annotation = holder.createInfoAnnotation(range
      /*new TextRange(expr.getTextRange.getEndOffset - 1, expr.getTextRange.getEndOffset)*/ , message)
    annotation.setEnforcedTextAttributes(new TextAttributes(null, null, color,
      effectType, Font.PLAIN))
    annotation.setAfterEndOfLine(false)
    annotation.setTooltip(tooltip)
  }

  private def checkMethodCallImplicitConversion(call: ScMethodCall, holder: AnnotationHolder) {
    val importUsed = call.getImportsUsed
    ImportTracker.getInstance(call.getProject).
      registerUsedImports(call.getContainingFile.asInstanceOf[ScalaFile], importUsed)
  }

  private def checkExpressionType(expr: ScExpression, holder: AnnotationHolder, typeAware: Boolean) {
    val ExpressionTypeResult(exprType, importUsed, implicitFunction) =
      expr.getTypeAfterImplicitConversion(expectedOption = expr.smartExpectedType())
    ImportTracker.getInstance(expr.getProject).
                    registerUsedImports(expr.getContainingFile.asInstanceOf[ScalaFile], importUsed)

    expr match {
      case m: ScMatchStmt =>
      case bl: ScBlock if bl.lastStatement != None =>
      case i: ScIfStmt if i.elseBranch != None =>
      case fun: ScFunctionExpr =>
      case tr: ScTryStmt =>
      case _ => {
        expr.getParent match {
          case args: ScArgumentExprList => return
          case inf: ScInfixExpr if inf.getArgExpr == expr => return
          case tuple: ScTuple if tuple.getContext.isInstanceOf[ScInfixExpr] &&
            tuple.getContext.asInstanceOf[ScInfixExpr].getArgExpr == tuple => return
          case e: ScParenthesisedExpr if e.getContext.isInstanceOf[ScInfixExpr] &&
            e.getContext.asInstanceOf[ScInfixExpr].getArgExpr == e => return
          case t: ScTypedStmt if t.isSequenceArg => return
          case parent@(_: ScTuple | _: ScParenthesisedExpr) =>
            parent.getParent match {
              case inf: ScInfixExpr if inf.getArgExpr == parent => return
              case _ =>
            }
          case param: ScParameter =>
            // TODO detect if the expected type is abstract (SCL-3508), if not check conformance
            return
          case ass: ScAssignStmt if ass.isNamedParameter => return //that's checked in application annotator
          case _ =>
        }

        expr.expectedTypeEx(false) match {
          case Some((tp: ScType, _)) if tp equiv Unit => //do nothing
          case Some((tp: ScType, typeElement)) => {
            import org.jetbrains.plugins.scala.lang.psi.types._
            val expectedType = Success(tp, None)
            if (settings(expr).SHOW_IMPLICIT_CONVERSIONS) {
              implicitFunction match {
                case Some(fun) => {
                  //todo:
                  /*val typeFrom = expr.getType(TypingContext.empty).getOrElse(Any)
                  val typeTo = exprType.getOrElse(Any)
                  val exprText = expr.getText
                  val range = expr.getTextRange
                  showImplicitUsageAnnotation(exprText, typeFrom, typeTo, fun, range, holder,
                    EffectType.LINE_UNDERSCORE, Color.LIGHT_GRAY)*/
                }
                case None => //do nothing
              }
            }
            val conformance = ScalaAnnotator.smartCheckConformance(expectedType, exprType)
            if (!conformance) {
              if (typeAware) {
                val error = ScalaBundle.message("expr.type.does.not.conform.expected.type",
                  ScType.presentableText(exprType.getOrNothing), ScType.presentableText(expectedType.get))
                val annotation: Annotation = holder.createErrorAnnotation(expr, error)
                annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                typeElement match {
                  case Some(te) => annotation.registerFix(new ChangeTypeFix(te, exprType.getOrNothing))
                  case None =>
                }
              }
            }
          }
          case _ => //do nothing
        }
      }
    }
  }

  private def checkExpressionImplicitParameters(expr: ScExpression, holder: AnnotationHolder) {
    expr.findImplicitParameters match {
      case Some(seq) => {
        for (resolveResult <- seq) {
          if (resolveResult != null) {
            ImportTracker.getInstance(expr.getProject).registerUsedImports(
              expr.getContainingFile.asInstanceOf[ScalaFile], resolveResult.importsUsed)
            registerUsedElement(expr, resolveResult, false)
          }
        }
      }
      case _ =>
    }
  }

  private def checkExplicitTypeForReturnStatement(ret: ScReturnStmt, holder: AnnotationHolder) {
    val fun: ScFunction = PsiTreeUtil.getParentOfType(ret, classOf[ScFunction])
    fun match {
      case null => {
        val error = ScalaBundle.message("return.outside.method.definition")
        val annotation: Annotation = holder.createErrorAnnotation(ret.returnKeyword, error)
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      }
      case _ if !fun.hasAssign || fun.returnType.exists(_ == Unit) => {
        return
      }
      case _ => fun.returnTypeElement match {
        case Some(x: ScTypeElement) => {
          import org.jetbrains.plugins.scala.lang.psi.types._
          val funType = fun.returnType
          funType match {
            case Success(tp: ScType, _) if tp equiv Unit => return //nothing to check
            case _ =>
          }
          val ExpressionTypeResult(_, importUsed, _) = ret.expr match {
            case Some(e: ScExpression) => e.getTypeAfterImplicitConversion()
            case None => ExpressionTypeResult(Success(Unit, None), Set.empty, None)
          }
          ImportTracker.getInstance(ret.getProject).registerUsedImports(ret.getContainingFile.asInstanceOf[ScalaFile], importUsed)
        }
        case _ =>
      }
    }
  }

  private def checkForStmtUsedTypes(f: ScForStatement, holder: AnnotationHolder) {
    ImportTracker.getInstance(f.getProject).registerUsedImports(f.getContainingFile.asInstanceOf[ScalaFile],
      ScalaPsiUtil.getExprImports(f))
  }

  private def checkImportExpr(impExpr: ScImportExpr, holder: AnnotationHolder) {
    if (impExpr.qualifier == null) {
      val annotation: Annotation = holder.createErrorAnnotation(impExpr.getTextRange,
          ScalaBundle.message("import.expr.should.be.qualified"))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }

  private def checkTypeElementForm(typeElement: ScTypeElement, holder: AnnotationHolder) {
    //todo: check bounds conformance for parameterized type
  }

  private def checkAnnotationType(annotation: ScAnnotation, holder: AnnotationHolder) {
    //todo: check annotation is inheritor for class scala.Annotation
  }
}

object ScalaAnnotator {
  val usedImportsKey: Key[HashSet[ImportUsed]] = Key.create("used.imports.key")

  /**
   * This method will return checked conformance if it's possible to check it.
   * In other way it will return true to avoid red code.
   * Check conformance in case l = r.
   */
  def smartCheckConformance(l: TypeResult[ScType], r: TypeResult[ScType]): Boolean = {
    for (leftType <- l; rightType <- r) {
      if (!Conformance.conforms(leftType, rightType)) {
        return false
      } else return true
    }
    true
  }
}