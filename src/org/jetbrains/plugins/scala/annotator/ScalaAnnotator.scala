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
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import params.{ScParameter, ScParameters, ScClassParameter}
import patterns.{ScInfixPattern, ScBindingPattern}
import quickfix.modifiers.{RemoveModifierQuickFix, AddModifierQuickFix}
import modifiers.ModifierChecker
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import quickfix.{ChangeTypeFix, ReportHighlightingErrorQuickFix, ImplementMethodsQuickFix}
import template._
import types.ScTypeElement
import scala.Some
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, TypeResult, Success}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import java.awt.{Font, Color}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import components.HighlightingAdvisor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions._
import collection.{Seq, Set}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ReadValueUsed, WriteValueUsed, ValueUsed, ImportUsed}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._

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

    if (!compiled && element.isInstanceOf[ScExpression]) {
      checkExpressionType(element.asInstanceOf[ScExpression], holder, typeAware)
      checkExpressionImplicitParameters(element.asInstanceOf[ScExpression], holder)
      ByNameParameter.annotate(element.asInstanceOf[ScExpression], holder, typeAware)
    }

    if (element.isInstanceOf[ScTypeElement]) {
      checkTypeElementForm(element.asInstanceOf[ScTypeElement], holder)
    }

    if (element.isInstanceOf[ScAnnotation]) {
      checkAnnotationType(element.asInstanceOf[ScAnnotation], holder)
    }

    if (element.isInstanceOf[ScForStatement]) {
      checkForStmtUsedTypes(element.asInstanceOf[ScForStatement], holder)
    }

    if (element.isInstanceOf[ScVariableDefinition]) {
      annotateVariableDefinition(element.asInstanceOf[ScVariableDefinition], holder, typeAware)
    }

    if (element.isInstanceOf[ScTypedStmt]) {
      annotateTypedStatement(element.asInstanceOf[ScTypedStmt], holder, typeAware)
    }

    if (!compiled && element.isInstanceOf[ScPatternDefinition]) {
      annotatePatternDefinition(element.asInstanceOf[ScPatternDefinition], holder, typeAware)
    }
    if (element.isInstanceOf[ScMethodCall]) {
      annotateMethodCall(element.asInstanceOf[ScMethodCall], holder)
    }
    if (element.isInstanceOf[ScSelfInvocation]) {
      checkSelfInvocation(element.asInstanceOf[ScSelfInvocation], holder)
    }
    if (element.isInstanceOf[ScConstrBlock]) {
      annotateAuxiliaryConstructor(element.asInstanceOf[ScConstrBlock], holder)
    }

    annotateScope(element, holder)

    if (isAdvancedHighlightingEnabled(element) && settings(element).SHOW_IMPLICIT_CONVERSIONS) {
      element match {
        case expr: ScExpression =>
          expr.getTypeExt(TypingContext.empty) match {
            case ExpressionTypeResult(Success(t, _), _, Some(implicitFunction)) =>
              highlightImplicitView(expr, implicitFunction, t, expr, holder)
            case _ =>
          }
        case _ =>
      }
    }

    element match {
      case a: ScAssignStmt => annotateAssignment(a, holder, typeAware)
      
      case ps: ScParameters => annotateParameters(ps, holder) 
      
      case f: ScFunctionDefinition if !compiled && !f.isConstructor => {
        annotateFunction(f, holder, typeAware)
      }
      
      case x: ScFunction if x.getParent.isInstanceOf[ScTemplateBody] => {
        //todo: unhandled case abstract override
        //checkOverrideMethods(x, holder)
      }
      case x: ScTemplateDefinition => {
        val tdParts = Seq(AbstractInstantiation, FinalClassInheritance, IllegalInheritance, ObjectCreationImpossible,
          MultipleInheritance, NeedsToBeAbstract, NeedsToBeTrait, SealedClassInheritance, UndefinedMember)
        tdParts.foreach(_.annotate(x, holder, typeAware))
        x match {
          case cls: ScClass =>
            val clsParts = Seq(CaseClassWithoutParamList, HasImplicitParamAndBound)
            clsParts.foreach(_.annotate(cls, holder, typeAware))
          case trt: ScTrait =>
            val traitParts = Seq(TraitHasImplicitBound)
            traitParts.foreach(_.annotate(trt, holder, typeAware))
          case _ => 
        }
      }
      case ref: ScReferenceElement => {
        if(typeAware) annotateReference(ref, holder)
        ref.qualifier match {
          case None => checkNotQualifiedReferenceElement(ref, holder)
          case Some(_) => checkQualifiedReferenceElement(ref, holder)
        }
      }
      case constructor: ScConstructor => {
        if(typeAware) annotateConstructor(constructor, holder)
      }
      case impExpr: ScImportExpr => {
        checkImportExpr(impExpr, holder)
      }
      case ret: ScReturnStmt => {
        checkExplicitTypeForReturnStatement(ret, holder)
      }
      case ml: ScModifierList => {
        ModifierChecker.checkModifiers(ml, holder)
      }
      case sTypeParam: ScTypeBoundsOwner => {
        checkTypeParamBounds(sTypeParam, holder)
      }
      case _ => AnnotatorHighlighter.highlightElement(element, holder)
    }

    super[ControlFlowInspections].annotate(element, holder)
  }

  private def settings(element: PsiElement): ScalaCodeStyleSettings = {
    CodeStyleSettingsManager.getSettings(element.getProject)
            .getCustomSettings(classOf[ScalaCodeStyleSettings])
  }

  def isAdvancedHighlightingEnabled(element: PsiElement): Boolean = {
    HighlightingAdvisor.getInstance(element.getProject).enabled
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
    if (resolve.length != 1) return
    resolve.apply(0) match {
      case res: ScalaResolveResult => {
        val memb: PsiMember = res.getElement match {
          case member: PsiMember => member
          case bind: ScBindingPattern => {
            ScalaPsiUtil.nameContext(bind) match {
              case member: PsiMember => member
              case _ => return
            }
          }
          case _ => return
        }
        if (!res.isNamedParameter && !ResolveUtils.isAccessible(memb, refElement)) {
          val error = ScalaBundle.message("element.is.not.accessible", refElement.refName)
          val annotation = holder.createErrorAnnotation(refElement.nameId, error)
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          //todo: fixes for changing access
        }
      }
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

  private def checkImplementedMethods(clazz: ScTemplateDefinition, holder: AnnotationHolder) {
    clazz match {
      case _: ScTrait => return
      case _ =>
    }
    if (clazz.hasModifierProperty("abstract")) return
    if (ScalaOIUtil.getMembersToImplement(clazz).length > 0) {
      val error = clazz match {
        case _: ScClass => ScalaBundle.message("class.must.declared.abstract", clazz.getName)
        case _: ScObject => ScalaBundle.message("object.must.implement", clazz.getName)
        case _: ScNewTemplateDefinition => ScalaBundle.message("anonymous.class.must.declared.abstract")
      }
      val start = clazz.getTextRange.getStartOffset
      val eb = clazz.extendsBlock
      val end = eb.templateBody match {
        case Some(x) => {
          val shifted = eb.findElementAt(x.getStartOffsetInParent - 1) match {case w: PsiWhiteSpace => w case _ => x}
          shifted.getTextRange.getStartOffset
        }
        case None => eb.getTextRange.getEndOffset
      }

      val annotation: Annotation = holder.createErrorAnnotation(new TextRange(start, end), error)
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      annotation.registerFix(new ImplementMethodsQuickFix(clazz))
    }
  }

  private def checkOverrideMethods(method: ScFunction, holder: AnnotationHolder) {
    val signatures = method.superSignatures
    if (signatures.length == 0) {
      if (method.hasModifierProperty("override")) {
        val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
          ScalaBundle.message("method.overrides.nothing", method.getName))
        annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        annotation.registerFix(new RemoveModifierQuickFix(method, "override"))
      }
    } else {
      if (!method.hasModifierProperty("override") && !method.isInstanceOf[ScFunctionDeclaration]) {
        def isConcrete(signature: Signature): Boolean = if (signature.namedElement != None)
          ScalaPsiUtil.nameContext(signature.namedElement.get) match {
            case _: ScFunctionDefinition => true
            case method: PsiMethod if !method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.isConstructor => true
            case _: ScPatternDefinition => true
            case _: ScVariableDeclaration => true
            case _: ScClassParameter => true
            case _ => false
          } else false
        def isConcretes: Boolean = {
          for (signature <- signatures if isConcrete(signature)) return true
          false
        }
        if (isConcretes) {
          val annotation: Annotation = holder.createErrorAnnotation(method.nameId.getTextRange,
            ScalaBundle.message("method.needs.override.modifier", method.getName))
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          annotation.registerFix(new AddModifierQuickFix(method, "override"))
        }
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

  private def checkExpressionType(expr: ScExpression, holder: AnnotationHolder, typeAware: Boolean) {
    val ExpressionTypeResult(exprType, importUsed, implicitFunction) =
      expr.getTypeAfterImplicitConversion(expectedOption = expr.smartExpectedType)
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

        expr.expectedTypeEx match {
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