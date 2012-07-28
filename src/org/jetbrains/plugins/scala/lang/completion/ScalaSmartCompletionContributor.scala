package org.jetbrains.plugins.scala.lang.completion

import handlers.{ScalaGenerateAnonymousFunctionInsertHandler, ScalaConstructorInsertHandler}
import lookups.{ScalaChainLookupElement, LookupElementManager, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.codeInsight.completion._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi._
import api.base.ScReferenceElement
import api.statements._
import api.toplevel.ScTypedDefinition
import api.toplevel.typedef._
import params.ScParameter
import types._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.patterns.{ElementPattern, StandardPatterns, PlatformPatterns}
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.psi._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.lookup._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import collection.mutable.{HashMap, HashSet, ArrayBuffer}
import org.jetbrains.plugins.scala.extensions.{toPsiNamedElementExt, toPsiClassExt}
import result.{Success, TypingContext}
import types.Conformance.AliasType
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{StdKinds, ScalaResolveResult, ResolveUtils}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends CompletionContributor {
  import ScalaSmartCompletionContributor._
  override def beforeCompletion(context: CompletionInitializationContext) {

  }

  private def acceptTypes(typez: Seq[ScType], variants: Array[Object], result: CompletionResultSet,
                          scope: GlobalSearchScope, secondCompletion: Boolean, completeThis: Boolean,
                          place: PsiElement, originalPlace: PsiElement) {
    def isAccessible(el: ScalaLookupItem): Boolean = {
      ScalaPsiUtil.nameContext(el.element) match {
        case memb: ScMember =>
          ResolveUtils.isAccessible(memb, place, true)
        case _ => true
      }
    }

    if (typez.length == 0 || typez.forall(_ == types.Nothing)) return

    def applyVariant(_variant: Object, checkForSecondCompletion: Boolean = false) {
      val chainVariant = _variant.isInstanceOf[ScalaChainLookupElement]
      val variant = _variant match {
        case el: ScalaLookupItem => el
        case ch: ScalaChainLookupElement => ch.element
        case _ => return
      }
      val elemToAdd = _variant.asInstanceOf[LookupElement]
      variant match {
        case el: ScalaLookupItem if isAccessible(el) => {
          val elem = el.element
          val subst = el.substitutor
          def checkType(_tp: ScType, _subst: ScSubstitutor, chainCompletion: Boolean, etaExpanded: Boolean = false): Boolean = {
            val tp = _subst.subst(_tp)
            var elementAdded = false
            val scType = subst.subst(tp)
            import types.Nothing
            if (!scType.equiv(Nothing) && typez.find(scType conforms _) != None) {
              elementAdded = true
              if (etaExpanded) el.etaExpanded = true
              result.addElement(elemToAdd)
            } else {
              typez.foreach {
                case ScParameterizedType(tp, Seq(arg)) if !elementAdded =>
                  ScType.extractClass(tp, Some(elem.getProject)) match {
                    case Some(clazz) if clazz.qualifiedName == "scala.Option" || clazz.getQualifiedName == "scala.Some" =>
                      if (!scType.equiv(Nothing) && scType.conforms(arg)) {
                        el.someSmartCompletion = true
                        if (etaExpanded) el.etaExpanded = true
                        result.addElement(elemToAdd)
                        elementAdded = true
                      }
                    case _ =>
                  }
                case _ =>
              }
            }
            if (!elementAdded && chainCompletion && secondCompletion) {
              val processor = new CompletionProcessor(StdKinds.refExprLastRef, place, false, postProcess = {
                r => {
                  r match {
                    case r: ScalaResolveResult if !r.isNamedParameter =>
                      import org.jetbrains.plugins.scala.lang.psi.types.Nothing
                      val qualifier = r.fromType.getOrElse(Nothing)
                      val newElem = LookupElementManager.getLookupElement(r, qualifierType = qualifier, isInStableCodeReference = false)(0)
                      applyVariant(new ScalaChainLookupElement(el, newElem))
                    case _ =>
                  }
                }
              })
              processor.processType(subst.subst(_tp), place)
              processor.candidatesS
            }
            elementAdded
          }
          if (!el.isNamedParameterOrAssignment)
            elem match {
              case fun: ScSyntheticFunction =>
                val second = checkForSecondCompletion && fun.paramClauses.flatten.length == 0
                checkType(fun.retType, ScSubstitutor.empty, second)
              case fun: ScFunction =>
                if (fun.containingClass != null && fun.containingClass.qualifiedName == "scala.Predef") {
                  fun.name match {
                    case "implicitly" | "identity" | "locally" => return
                    case _ =>
                  }
                }
                val infer = if (chainVariant) ScSubstitutor.empty else ScalaPsiUtil.inferMethodTypesArgs(fun, subst)
                val second = checkForSecondCompletion &&
                  fun.paramClauses.clauses.filterNot(_.isImplicit).flatMap(_.parameters).length == 0
                val added = fun.returnType match {
                  case Success(tp, _) => checkType(tp, infer, second)
                  case _ => false
                }
                if (!added) {
                  fun.getType(TypingContext.empty) match {
                    case Success(tp, _) => checkType(tp, infer, second, true)
                    case _ =>
                  }
                }
              case method: PsiMethod =>
                val second = checkForSecondCompletion && method.getParameterList.getParametersCount == 0
                val infer = if (chainVariant) ScSubstitutor.empty else ScalaPsiUtil.inferMethodTypesArgs(method, subst)
                checkType(ScType.create(method.getReturnType, method.getProject, scope), infer, second)
              case typed: ScTypedDefinition =>
                if (!PsiTreeUtil.isContextAncestor(typed.nameContext, place, false) &&
                  (originalPlace == null || !PsiTreeUtil.isContextAncestor(typed.nameContext, originalPlace, false)))
                  for (tt <- typed.getType(TypingContext.empty)) checkType(tt, ScSubstitutor.empty, checkForSecondCompletion)
              case f: PsiField =>
                checkType(ScType.create(f.getType, f.getProject, scope), ScSubstitutor.empty, checkForSecondCompletion)
              case _ =>
            }
        }
        case _ =>
      }
    }

    place.getParent match {
      case ref: ScReferenceExpression if ref.smartQualifier == None =>
        //enum and factory methods
        for (tp <- typez) {
          def checkObject(o: ScObject) {
            o.members.foreach {
              case function: ScFunction =>
                applyVariant(LookupElementManager.getLookupElement(new ScalaResolveResult(function), isClassName = true,
                  isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0))
              case v: ScValue =>
                v.declaredElements.foreach(td => {
                  applyVariant(LookupElementManager.getLookupElement(new ScalaResolveResult(td), isClassName = true,
                    isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0))
                })
              case v: ScVariable =>
                v.declaredElements.foreach(td => {
                  applyVariant(LookupElementManager.getLookupElement(new ScalaResolveResult(td), isClassName = true,
                    isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0))
                })
              case o: ScObject =>
                applyVariant(LookupElementManager.getLookupElement(new ScalaResolveResult(o), isClassName = true,
                  isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0))
              case _ =>
            }
          }
          def checkTypeProjection(tp: ScType) {
            tp match {
              case ScProjectionType(proj, _: ScTypeAlias | _: ScClass | _: ScTrait, subst, _) =>
                ScType.extractClass(proj) match {
                  case Some(o: ScObject) if ResolveUtils.isAccessible(o, place, true) && ScalaPsiUtil.hasStablePath(o) => checkObject(o)
                  case _ =>
                }
              case _ =>
            }
          }
          def checkType(tp: ScType) {
            ScType.extractClass(tp) match {
              case Some(c: ScClass) if c.qualifiedName == "scala.Option" || c.qualifiedName == "scala.Some" =>
                tp match {
                  case ScParameterizedType(_, Seq(tp)) => checkType(tp)
                  case _ =>
                }
              case Some(o: ScObject) => //do nothing
              case Some(clazz: ScTypeDefinition) =>
                checkTypeProjection(tp)
                ScalaPsiUtil.getCompanionModule(clazz) match {
                  case Some(o: ScObject) if ResolveUtils.isAccessible(o, place, true) && ScalaPsiUtil.hasStablePath(o) => checkObject(o)
                  case _ => //do nothing
                }
              case Some(p: PsiClass) if ResolveUtils.isAccessible(p, place, true) =>
                p.getAllMethods.foreach(method => {
                  if (method.hasModifierProperty("static") && ResolveUtils.isAccessible(method, place, true)) {
                    applyVariant(LookupElementManager.getLookupElement(new ScalaResolveResult(method), isClassName = true,
                      isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0))
                  }
                })
                p.getFields.foreach(field => {
                  if (field.hasModifierProperty("static") && ResolveUtils.isAccessible(field, place, true)) {
                    applyVariant(LookupElementManager.getLookupElement(new ScalaResolveResult(field), isClassName = true,
                      isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0))
                  }
                })
              case _ => checkTypeProjection(tp)
            }
          }
          checkType(tp)
        }
        variants.foreach(applyVariant(_, true))
        if (typez.find(_.equiv(types.Boolean)) != None) {
          for (keyword <- Set("false", "true")) {
            result.addElement(LookupElementManager.getKeywrodLookupElement(keyword, place))
          }
        }
        if (completeThis) {
          var parent = place
          var foundClazz = false
          while (parent != null) {
            parent match {
              case t: ScNewTemplateDefinition if foundClazz => //do nothing, impossible to invoke
              case t: ScTemplateDefinition =>
                t.getTypeWithProjections(TypingContext.empty, true) match {
                  case Success(scType, _) =>
                    import types.Nothing
                    val lookupString = (if (foundClazz) t.name + "." else "") + "this"
                    val el = new ScalaLookupItem(t, lookupString)
                    if (!scType.equiv(Nothing) && typez.find(scType conforms _) != None) {
                      if (!foundClazz) el.bold = true
                      result.addElement(el)
                    } else {
                      var elementAdded = false
                      typez.foreach {
                        case ScParameterizedType(tp, Seq(arg)) if !elementAdded =>
                          ScType.extractClass(tp, Some(place.getProject)) match {
                            case Some(clazz) if clazz.qualifiedName == "scala.Option" || clazz.qualifiedName == "scala.Some" =>
                              if (!scType.equiv(Nothing) && scType.conforms(arg)) {
                                el.someSmartCompletion = true
                                result.addElement(el)
                                elementAdded = true
                              }
                            case _ =>
                          }
                        case _ =>
                      }
                    }
                  case _ =>
                }
                foundClazz = true
              case _ =>
            }
            parent = parent.getContext
          }
        }
      case _ => variants.foreach(applyVariant(_, true))
    }
  }


  /*
    ref = expr
    expr = ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScAssignStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val assign = ref.getParent.asInstanceOf[ScAssignStmt]
      if (assign.getRExpression == Some(ref)) {
        assign.getLExpression match {
          case call: ScMethodCall => //todo: it's update method
          case leftExpression: ScExpression => {
            //we can expect that the type is same for left and right parts.
            acceptTypes(ref.expectedTypes(), ref.getVariants, result,
              ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
          }
        }
      } else { //so it's left expression
        //todo: if right expression exists?
      }
    }
  })

  /*
    val x: Type = ref
    var y: Type = ref
   */
  extend(CompletionType.SMART, StandardPatterns.or[PsiElement](superParentPattern(classOf[ScPatternDefinition]),
    superParentPattern(classOf[ScVariableDefinition])), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType().toList.toSeq, ref.getVariants, result, ref.getResolveScope,
        parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
    return ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScReturnStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val fun: ScFunction = PsiTreeUtil.getParentOfType(ref, classOf[ScFunction])
      if (fun == null) return
      acceptTypes(Seq[ScType](fun.returnType.getOrAny), ref.getVariants, result,
        ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  private def argumentsForFunction(args: ScArgumentExprList, referenceExpression: ScReferenceExpression,
                           result: CompletionResultSet) {
    val braceArgs = args.isBraceArgs
    val expects = referenceExpression.expectedTypes()
    for (expected <- expects) {
      def params(tp: ScType): Seq[ScType] = tp match {
        case ScFunctionType(_, params) => params
        case p: ScParameterizedType if p.getFunctionType != None =>
          p.getFunctionType match {
            case Some(ScFunctionType(_, params)) => params
            case _ => null
          }
        case _ => Conformance.isAliasType(tp) match {
          case Some(AliasType(_, Success(lowerType, _), _)) => params(lowerType)
          case _ => null
        }
      }
      val actualParams = params(expected)
      if (actualParams != null) {
        val params = actualParams match {
          case Seq(ScTupleType(params)) if braceArgs => params
          case Seq(p: ScParameterizedType) if p.getTupleType != None => p.getTupleType match {
            case Some(ScTupleType(params)) if braceArgs => params
            case _ => actualParams
          }
          case _ => actualParams
        }
        val presentableParams = params.map(_.removeAbstracts)
        var builder = LookupElementBuilder.create("")
        builder = builder.setRenderer(new LookupElementRenderer[LookupElement] {
          def renderElement(element: LookupElement, presentation: LookupElementPresentation) {
            val text = ScalaCompletionUtil.generateAnonymousFunctionText(braceArgs, presentableParams, false)
            presentation match {
              case realPresentation: RealLookupElementPresentation =>
                if (!realPresentation.hasEnoughSpaceFor(text, false)) {
                  var prefixIndex = presentableParams.length - 1
                  val suffix = ", ... =>"
                  var end = false
                  while (prefixIndex > 0 && !end) {
                    val prefix = ScalaCompletionUtil.generateAnonymousFunctionText(braceArgs,
                      presentableParams.slice(0, prefixIndex), false, true)
                    if (realPresentation.hasEnoughSpaceFor(prefix + suffix, false)) {
                      presentation.setItemText(prefix + suffix)
                      end = true
                    } else prefixIndex -= 1
                  }
                  if (!end) {
                    presentation.setItemText("... => ")
                  }
                } else presentation.setItemText(text)
                presentation.setIcon(Icons.LAMBDA)
              case _ =>
                presentation.setItemText(text)
            }
          }
        })
        builder = builder.setInsertHandler(new ScalaGenerateAnonymousFunctionInsertHandler(params, braceArgs))
        val lookupElement =
          if (ApplicationManager.getApplication.isUnitTestMode)
            builder.withAutoCompletionPolicy(AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE)
          else builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
        result.addElement(lookupElement)
      }
    }
  }

  /*
    call(exprs, ref, exprs)
    if expected type is function, so we can suggest anonymous function creation
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScArgumentExprList]),
    new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val referenceExpression = element.getParent.asInstanceOf[ScReferenceExpression]
      val args = referenceExpression.getParent.asInstanceOf[ScArgumentExprList]
      argumentsForFunction(args, referenceExpression, result)
    }
  })

  extend(CompletionType.SMART, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER),
    new CompletionProvider[CompletionParameters]() {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      "for test"
    }
  })

  /*
    call {ref}
    if expected type is function, so we can suggest anonymous function creation
   */
  extend(CompletionType.SMART, bracesCallPattern,
    new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val referenceExpression = element.getParent.asInstanceOf[ScReferenceExpression]
      val block = referenceExpression.getParent.asInstanceOf[ScBlockExpr]
      val args = block.getParent.asInstanceOf[ScArgumentExprList]
      argumentsForFunction(args, referenceExpression, result)
    }
  })

  /*
    call(exprs, ref, exprs)
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScArgumentExprList]),
    new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val referenceExpression = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(referenceExpression.expectedTypes(), referenceExpression.getVariants, result,
        referenceExpression.getResolveScope, parameters.getInvocationCount > 1,ScalaCompletionUtil.completeThis(referenceExpression),
        element, parameters.getOriginalPosition)
    }
  })

  /*
    if (ref) expr
    if (expr) ref
    if (expr) expr else ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScIfStmt]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                         result: CompletionResultSet) {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val ifStmt = ref.getParent.asInstanceOf[ScIfStmt]
        if (ifStmt.condition.getOrElse(null: ScExpression) == ref)
          acceptTypes(ref.expectedTypes(), ref.getVariants, result,
            ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
        else acceptTypes(ifStmt.expectedTypes(), ref.getVariants, result,
          ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    })

  /*
    while (ref) expr
    while (expr) ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScWhileStmt]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                         result: CompletionResultSet) {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val whileStmt = ref.getParent.asInstanceOf[ScWhileStmt]
        if (whileStmt.condition.getOrElse(null: ScExpression) == ref)
          acceptTypes(ref.expectedTypes(), ref.getVariants, result,
            ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
        else acceptTypes(ref.expectedTypes(), ref.getVariants, result,
          ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    })

  /*
   do expr while (ref)
   do ref while (expr)
  */
  extend(CompletionType.SMART, superParentPattern(classOf[ScDoStmt]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                         result: CompletionResultSet) {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val doStmt = ref.getParent.asInstanceOf[ScDoStmt]
        if (doStmt.condition.getOrElse(null: ScExpression) == ref)
          acceptTypes(ref.expectedTypes(), ref.getVariants, result,
            ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
        else acceptTypes(ref.expectedTypes(), ref.getVariants, result,
          ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    })

  /*
    expr op ref
    expr ref name
    ref op expr
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScInfixExpr]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val infix = ref.getParent.asInstanceOf[ScInfixExpr]
        val typez: ArrayBuffer[ScType] = new ArrayBuffer[ScType]
        if (infix.lOp == ref) {
          val op: String = infix.operation.getText
          if (op.endsWith(":")) {
            typez ++= ref.expectedTypes()
          }
        } else if (infix.rOp == ref) {
          val op: String = infix.operation.getText
          if (!op.endsWith(":")) {
            typez ++= ref.expectedTypes()
          }
        }
        acceptTypes(typez, ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
          ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
      }
    })

  /*
    inside try block according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScTryBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
   inside block expression according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScBlockExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
   inside finally block
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFinallyBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
   inside anonymous function
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType().toList.toSeq, ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
   for function definitions
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionDefinition]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
   for default parameters
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScParameter]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  extend(CompletionType.SMART, afterNewPattern, new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = parameters.getPosition

        val refElement = ScalaPsiUtil.getParentOfType(element, classOf[ScReferenceElement])

        val renamesMap = new HashMap[String, (String, PsiNamedElement)]()
        val reverseRenamesMap = new HashMap[String, PsiNamedElement]()

        refElement match {
          case ref: PsiReference => ref.getVariants().foreach {
            case s: ScalaLookupItem =>
              s.isRenamed match {
                case Some(name) =>
                  renamesMap += ((s.element.name, (name, s.element)))
                  reverseRenamesMap += ((name, s.element))
                case None =>
              }
            case _ =>
          }
          case _ =>
        }

        val addedClasses = new HashSet[String]
        val newExpr = PsiTreeUtil.getParentOfType(element, classOf[ScNewTemplateDefinition])
        val types: Array[ScType] = newExpr.expectedTypes().map(tp => tp match {
          case ScAbstractType(_, lower, upper) => upper
          case _ => tp
        })
        for (typez <- types) {
          val element: LookupElement = convertTypeToLookupElement(typez, newExpr, addedClasses,
            new AfterNewLookupElementRenderer(_, _, _), new ScalaConstructorInsertHandler, renamesMap)
          if (element != null) {
            result.addElement(element)
          }
        }

        for (typez <- types) {
          collectInheritorsForType(typez, newExpr, addedClasses, result, new AfterNewLookupElementRenderer(_, _, _),
            new ScalaConstructorInsertHandler, renamesMap)
        }
      }
    })
}

private[completion] object ScalaSmartCompletionContributor {
  def superParentPattern(clazz: java.lang.Class[_ <: PsiElement]): ElementPattern[PsiElement] = {
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceExpression]).
          withSuperParent(2, clazz)
  }

  def superParentsPattern(classes: Class[_ <: PsiElement]*): ElementPattern[PsiElement] = {
    var pattern: Capture[PsiElement] =
      PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classes(0))
    for (i <- 1 until classes.length) {
      pattern = pattern.withSuperParent(i + 1, classes(i))
    }
    pattern
  }
  val bracesCallPattern = superParentsPattern(classOf[ScReferenceExpression], classOf[ScBlockExpr],
    classOf[ScArgumentExprList], classOf[ScMethodCall])
}