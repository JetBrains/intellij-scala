package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.util.PsiTreeUtil
import controlFlow.ControlFlowInspections
import createFromUsage._
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
import patterns.{ScConstructorPattern, ScPattern, ScInfixPattern}
import processor.MethodResolveProcessor
import modifiers.ModifierChecker
import com.intellij.psi._
import quickfix.{ChangeTypeFix, ReportHighlightingErrorQuickFix}
import template._
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import java.awt.{Font, Color}
import components.HighlightingAdvisor
import org.jetbrains.plugins.scala.extensions._
import collection.{mutable, Seq, Set}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ReadValueUsed, WriteValueUsed, ValueUsed, ImportUsed}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiManager, ScalaPsiElementFactory}
import result.{TypingContext, TypeResult, Success}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import highlighter.{DefaultHighlighter, AnnotatorHighlighter}
import types.{ScParameterizedTypeElement, ScTypeProjection, ScTypeElement}
import com.intellij.openapi.util.{TextRange, Key}
import collection.mutable.{ArrayBuffer, HashSet}
import settings._
import com.intellij.lang.ASTNode
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedStringPrefixReference

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */
class ScalaAnnotator extends Annotator with FunctionAnnotator with ScopeAnnotator
with ParametersAnnotator with ApplicationAnnotator
with AssignmentAnnotator with VariableDefinitionAnnotator
with TypedStatementAnnotator with PatternDefinitionAnnotator
with ConstructorPatternAnnotator with ControlFlowInspections
with ConstructorAnnotator with OverridingAnnotator
with DumbAware {
  override def annotate(element: PsiElement, holder: AnnotationHolder) {
    val compiled = element.getContainingFile match {
      case file: ScalaFile => file.isCompiled
      case _ => false
    }

    val typeAware = isAdvancedHighlightingEnabled(element)

    val visitor = new ScalaElementVisitor {
      private def expressionPart(expr: ScExpression) {
        if (!compiled) {
          checkExpressionType(expr, holder, typeAware)
          checkExpressionImplicitParameters(expr, holder)
          ByNameParameter.annotate(expr, holder, typeAware)
        }

        if (isAdvancedHighlightingEnabled(element)) {
          expr.getTypeExt(TypingContext.empty) match {
            case ExpressionTypeResult(Success(t, _), _, Some(implicitFunction)) =>
              highlightImplicitView(expr, implicitFunction, t, expr, holder)
            case _ =>
          }
        }
      }

      override def visitParameterizedTypeElement(parameterized: ScParameterizedTypeElement) {
        val tp = parameterized.typeElement.getType(TypingContext.empty)
        tp match {
          case Success(res, _) =>
            ScType.extractDesignated(res) match {
              case Some((t: ScTypeParametersOwner, subst)) =>
                val typeParametersLength = t.typeParameters.length
                val argsLength = parameterized.typeArgList.typeArgs.length
                if (typeParametersLength != argsLength) {
                  val error = "Wrong number of type parameters. Expected: " + typeParametersLength + ", actual: " + argsLength
                  val leftBracket = parameterized.typeArgList.getNode.findChildByType(ScalaTokenTypes.tLSQBRACKET)
                  if (leftBracket != null) {
                    val annotation = holder.createErrorAnnotation(leftBracket, error)
                    annotation.setHighlightType(ProblemHighlightType.ERROR)
                  }
                  val rightBracket = parameterized.typeArgList.getNode.findChildByType(ScalaTokenTypes.tRSQBRACKET)
                  if (rightBracket != null) {
                    val annotation = holder.createErrorAnnotation(rightBracket, error)
                    annotation.setHighlightType(ProblemHighlightType.ERROR)
                  }
                }
              case _ =>
            }
          case _ =>
        }
        super.visitParameterizedTypeElement(parameterized)
      }

      override def visitExpression(expr: ScExpression) {
        expressionPart(expr)
        super.visitExpression(expr)
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
        checkTypeElementForm(te, holder)
        super.visitTypeElement(te)
      }


      override def visitLiteral(l: ScLiteral) {
        if (l.isInstanceOf[ScInterpolatedStringLiteral] && l.getFirstChild != null) {
          highlightWrongInterpolatedString(l.asInstanceOf[ScInterpolatedStringLiteral], holder)
        }
        super.visitLiteral(l)
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

      override def visitPattern(pat: ScPattern) {
        pat match {
          case consPat: ScConstructorPattern =>
            annotateConstructorPattern(consPat, holder, typeAware)
          case _ =>
        }
        super.visitPattern(pat)
      }

      override def visitMethodCallExpression(call: ScMethodCall) {
        checkMethodCallImplicitConversion(call, holder)
        if (typeAware) annotateMethodCall(call, holder)
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

      override def visitUnderscoreExpression(under: ScUnderscoreSection) {
        checkUnboundUnderscore(under, holder)
      }

      private def referencePart(ref: ScReferenceElement) {
        if (typeAware) annotateReference(ref, holder)
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
        if (typeAware) annotateConstructor(constr, holder)
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

  def isAdvancedHighlightingEnabled(element: PsiElement): Boolean = {
    if (!HighlightingAdvisor.getInstance(element.getProject).enabled) return false
    val containingFile = element.getContainingFile
    def calculate(): HashSet[TextRange] = {
      val text = containingFile.getText
      val indexes = new ArrayBuffer[Int]
      var lastIndex = 0
      while (text.indexOf("/*_*/", lastIndex) >= 0) {
        lastIndex = text.indexOf("/*_*/", lastIndex) + 5
        indexes += lastIndex
      }
      if (indexes.length == 0) return HashSet.empty
      if (indexes.length % 2 != 0) indexes += text.length

      val res = new HashSet[TextRange]
      for (i <- 0 until indexes.length by 2) {
        res += new TextRange(indexes(i), indexes(i + 1))
      }
      res
    }
    var data = containingFile.getUserData(ScalaAnnotator.ignoreHighlightingKey)
    val count = containingFile.getManager.getModificationTracker.getModificationCount
    if (data == null || data._1 != count) {
      data = (count, calculate())
      containingFile.putUserData(ScalaAnnotator.ignoreHighlightingKey, data)
    }
    val offset = element.getTextOffset
    data._2.forall(!_.contains(offset))
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
                t.expectedTypeEx(fromUnderscore = false) match {
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
        checkMember("isDefinedAt", checkReturnTypeIsBoolean = true)
        checkMember("apply", checkReturnTypeIsBoolean = false)
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
        case e: ScReferenceExpression => processError(countError = false, fixes = getFix)
        case e: ScStableCodeReferenceElement if e.getParent.isInstanceOf[ScInfixPattern] &&
                e.getParent.asInstanceOf[ScInfixPattern].refernece == e => //todo: this is hide A op B in patterns
        case _ => refElement.getParent match {
          case s: ScImportSelector if resolve.length > 0 =>
          case _ => processError(countError = true, fixes = getFix)
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
      registerUsedElement(refElement, scalaResult, checkWrite = true)
    }

    checkAccessForReference(resolve, refElement, holder)
    checkForwardReference(resolve, refElement, holder)

    if (resolve.length == 1) {
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

    val typeTo = resolveResult.implicitType match {
      case Some(tp) => tp
      case _ => Any
    }
    highlightImplicitView(expr, fun, typeTo, refElement.nameId, holder)
  }

  private def highlightImplicitView(expr: ScExpression, fun: PsiNamedElement, typeTo: ScType,
                                    elementToHighlight: PsiElement, holder: AnnotationHolder) {
    val range = elementToHighlight.getTextRange
    val annotation: Annotation = holder.createInfoAnnotation(range, null)
    annotation.setTextAttributes(DefaultHighlighter.IMPLICIT_CONVERSIONS)
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
      registerUsedElement(refElement, scalaResult, checkWrite = true)
    }
    checkAccessForReference(resolve, refElement, holder)
    if (refElement.isInstanceOf[ScExpression] &&
            resolve.length == 1) {
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
        val error = "Symbol %s is inaccessible from this place".format(r.element.name)
        val annotation = holder.createErrorAnnotation(refElement.nameId, error)
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      //todo: add fixes
      case _ =>
    }
  }

  private def highlightWrongInterpolatedString(l: ScInterpolatedStringLiteral, holder: AnnotationHolder) {
    val ref = l.findReferenceAt(0)
    val prefix = l.getFirstChild
    val injections = l.getInjections
    
    ref match {
      case _: ScInterpolatedStringPrefixReference =>
      case _ => return
    }

    def annotateBadPrefix(key: String) {
      val annotation = holder.createErrorAnnotation(prefix.getTextRange,
        ScalaBundle.message(key, prefix.getText))
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
    }
    
    ref.resolve() match {
      case r: ScFunctionDefinition =>
        val elementsMap = mutable.HashMap[Int, PsiElement]()
        val params = new mutable.StringBuilder("(")

        injections.foreach { i =>
          elementsMap += params.length -> i
          params.append(i.getText).append(",")
        }
        if (injections.length > 0) params.setCharAt(params.length - 1, ')') else params.append(')')
        val expr = l.getStringContextExpression.get
        val shift = "StringContext(str).".length + r.getName.length  + expr.getTextRange.getStartOffset

        val fakeAnnotator = new AnnotationHolderImpl(Option(holder.getCurrentAnnotationSession)
                .getOrElse(new AnnotationSession(l.getContainingFile))) {
          override def createErrorAnnotation(elt: PsiElement, message: String): Annotation =
            createErrorAnnotation(elt.getTextRange, message)

          override def createErrorAnnotation(range: TextRange, message: String): Annotation = {
            holder.createErrorAnnotation(elementsMap.get(range.getStartOffset - shift).getOrElse(prefix), message)
          }
        }

        annotateReference(expr.asInstanceOf[ScMethodCall].getEffectiveInvokedExpr.
          asInstanceOf[ScReferenceElement], fakeAnnotator)
      case _: PsiElement => annotateBadPrefix("=(")
      case _ => annotateBadPrefix("cannot.resolve.in.StringContext")
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

  private def checkMethodCallImplicitConversion(call: ScMethodCall, holder: AnnotationHolder) {
    val importUsed = call.getImportsUsed
    ImportTracker.getInstance(call.getProject).
            registerUsedImports(call.getContainingFile.asInstanceOf[ScalaFile], importUsed)
  }

  private def checkExpressionType(expr: ScExpression, holder: AnnotationHolder, typeAware: Boolean) {
    def checkExpressionTypeInner(fromUnderscore: Boolean) {
      val ExpressionTypeResult(exprType, importUsed, implicitFunction) =
        expr.getTypeAfterImplicitConversion(expectedOption = expr.smartExpectedType(fromUnderscore),
          fromUnderscore = fromUnderscore)
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

          expr.expectedTypeEx(fromUnderscore) match {
            case Some((tp: ScType, _)) if tp equiv Unit => //do nothing
            case Some((tp: ScType, typeElement)) => {
              import org.jetbrains.plugins.scala.lang.psi.types._
              val expectedType = Success(tp, None)
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
              val conformance = ScalaAnnotator.smartCheckConformance(expectedType, exprType)
              if (!conformance) {
                if (typeAware) {
                  val error = ScalaBundle.message("expr.type.does.not.conform.expected.type",
                    ScType.presentableText(exprType.getOrNothing), ScType.presentableText(expectedType.get))
                  val markedPsi = (expr, expr.getParent) match {
                    case (b: ScBlockExpr, _) => b.getRBrace.map(_.getPsi).getOrElse(expr)
                    case (_, b: ScBlockExpr) => b.getRBrace.map(_.getPsi).getOrElse(expr)
                    case _ => expr
                  }
                  val annotation: Annotation = holder.createErrorAnnotation(markedPsi, error)
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
    if (ScUnderScoreSectionUtil.isUnderscoreFunction(expr)) {
      checkExpressionTypeInner(fromUnderscore = true)
    }
    checkExpressionTypeInner(fromUnderscore = false)
  }

  private def checkExpressionImplicitParameters(expr: ScExpression, holder: AnnotationHolder) {
    expr.findImplicitParameters match {
      case Some(seq) => {
        for (resolveResult <- seq) {
          if (resolveResult != null) {
            ImportTracker.getInstance(expr.getProject).registerUsedImports(
              expr.getContainingFile.asInstanceOf[ScalaFile], resolveResult.importsUsed)
            registerUsedElement(expr, resolveResult, checkWrite = false)
          }
        }
      }
      case _ =>
    }
  }

  private def checkUnboundUnderscore(under: ScUnderscoreSection, holder: AnnotationHolder) {
    if (under.getText == "_") {
      ScalaPsiUtil.getParentOfType(under, classOf[ScVariableDefinition]) match {
        case varDef @ ScVariableDefinition.expr(expr) if varDef.expr == Some(under) =>
          if (varDef.containingClass == null) {
            val error = ScalaBundle.message("local.variables.must.be.initialized")
            val annotation: Annotation = holder.createErrorAnnotation(under, error)
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          } else if (varDef.typeElement.isEmpty) {
            val error = ScalaBundle.message("unbound.placeholder.parameter")
            val annotation: Annotation = holder.createErrorAnnotation(under, error)
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          }
        case _ =>
          // TODO SCL-2610 properly detect unbound placeholders, e.g. ( { _; (_: Int) } ) and report them.
          //  val error = ScalaBundle.message("unbound.placeholder.parameter")
          //  val annotation: Annotation = holder.createErrorAnnotation(under, error)
          //  annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
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
  val ignoreHighlightingKey: Key[(Long, HashSet[TextRange])] = Key.create("ignore.highlighting.key")

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