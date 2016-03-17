package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.patterns.{ElementPattern, PlatformPatterns, StandardPatterns}
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.completion.ScalaAfterNewCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.handlers.{ScalaConstructorInsertHandler, ScalaGenerateAnonymousFunctionInsertHandler}
import org.jetbrains.plugins.scala.lang.completion.lookups.{LookupElementManager, ScalaChainLookupElement, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends ScalaCompletionContributor {
  import org.jetbrains.plugins.scala.lang.completion.ScalaSmartCompletionContributor._

  private def acceptTypes(typez: Seq[ScType], variants: Array[Object], result: CompletionResultSet,
                          scope: GlobalSearchScope, secondCompletion: Boolean, completeThis: Boolean,
                          place: PsiElement, originalPlace: PsiElement) {
    implicit val typeSystem = scope.getProject.typeSystem
    def isAccessible(el: ScalaLookupItem): Boolean = {
      ScalaPsiUtil.nameContext(el.element) match {
        case memb: ScMember =>
          ResolveUtils.isAccessible(memb, place, forCompletion = true)
        case _ => true
      }
    }

    if (typez.isEmpty || typez.forall(_ == types.Nothing)) return

    def applyVariant(_variant: Object, checkForSecondCompletion: Boolean = false) {
      val chainVariant = _variant.isInstanceOf[ScalaChainLookupElement]
      val variant = _variant match {
        case el: ScalaLookupItem => el
        case ch: ScalaChainLookupElement => ch.element
        case _ => return
      }
      val elemToAdd = _variant.asInstanceOf[LookupElement]
      variant match {
        case el: ScalaLookupItem if isAccessible(el) =>
          val elem = el.element
          val subst = el.substitutor
          def checkType(_tp: ScType, _subst: ScSubstitutor, chainCompletion: Boolean, etaExpanded: Boolean = false): Boolean = {
            val tp = _subst.subst(_tp)
            var elementAdded = false
            val scType = subst.subst(tp)
            import org.jetbrains.plugins.scala.lang.psi.types.Nothing
            if (!scType.equiv(Nothing) && typez.exists(scType conforms _)) {
              elementAdded = true
              if (etaExpanded) el.etaExpanded = true
              result.addElement(elemToAdd)
            } else {
              typez.foreach {
                case ScParameterizedType(tpe, Seq(arg)) if !elementAdded =>
                  ScType.extractClass(tpe, Some(elem.getProject)) match {
                    case Some(clazz) if clazz.qualifiedName == "scala.Option" || clazz.qualifiedName == "scala.Some" =>
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
                      val newElem = LookupElementManager.getLookupElement(r, qualifierType = qualifier, isInStableCodeReference = false).head
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
                val second = checkForSecondCompletion && fun.paramClauses.flatten.isEmpty
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
                  fun.paramClauses.clauses.filterNot(_.isImplicit).flatMap(_.parameters).isEmpty
                val added = fun.returnType match {
                  case Success(tp, _) => checkType(tp, infer, second)
                  case _ => false
                }
                if (!added) {
                  fun.getType(TypingContext.empty) match {
                    case Success(tp, _) => checkType(tp, infer, second, etaExpanded = true)
                    case _ =>
                  }
                }
              case method: PsiMethod =>
                val second = checkForSecondCompletion && method.getParameterList.getParametersCount == 0
                val infer = if (chainVariant) ScSubstitutor.empty else ScalaPsiUtil.inferMethodTypesArgs(method, subst)
                checkType(method.getReturnType.toScType(method.getProject, scope), infer, second)
              case typed: ScTypedDefinition =>
                if (!PsiTreeUtil.isContextAncestor(typed.nameContext, place, false) &&
                  (originalPlace == null || !PsiTreeUtil.isContextAncestor(typed.nameContext, originalPlace, false)))
                  for (tt <- typed.getType(TypingContext.empty)) checkType(tt, ScSubstitutor.empty, checkForSecondCompletion)
              case f: PsiField =>
                checkType(f.getType.toScType(f.getProject, scope), ScSubstitutor.empty, checkForSecondCompletion)
              case _ =>
            }
        case _ =>
      }
    }

    place.getContext match {
      case ref: ScReferenceExpression if ref.smartQualifier.isEmpty =>
        //enum and factory methods
        val iterator = typez.iterator
        while (iterator.hasNext) {
          val tp = iterator.next()
          def checkObject(o: ScObject) {
            o.members.foreach {
              case function: ScFunction =>
                val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(function), isClassName = true,
                  isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).head
                lookup.addLookupStrings(o.name + "." + function.name)
                applyVariant(lookup)
              case v: ScValue =>
                v.declaredElements.foreach(td => {
                  val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(td), isClassName = true,
                    isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).head
                  lookup.addLookupStrings(o.name + "." + td.name)
                  applyVariant(lookup)
                })
              case v: ScVariable =>
                v.declaredElements.foreach(td => {
                  val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(td), isClassName = true,
                    isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).head
                  lookup.addLookupStrings(o.name + "." + td.name)
                  applyVariant(lookup)
                })
              case obj: ScObject =>
                val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(obj), isClassName = true,
                  isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).head
                lookup.addLookupStrings(o.name + "." + obj.name)
                applyVariant(lookup)
              case _ =>
            }
          }
          def checkTypeProjection(tp: ScType) {
            tp match {
              case ScProjectionType(proj, _: ScTypeAlias | _: ScClass | _: ScTrait, _) =>
                ScType.extractClass(proj) match {
                  case Some(o: ScObject) if ResolveUtils.isAccessible(o, place, forCompletion = true) && ScalaPsiUtil.hasStablePath(o) => checkObject(o)
                  case _ =>
                }
              case _ =>
            }
          }
          @tailrec
          def checkType(tp: ScType) {
            ScType.extractClass(tp) match {
              case Some(c: ScClass) if c.qualifiedName == "scala.Option" || c.qualifiedName == "scala.Some" =>
                tp match {
                  case ScParameterizedType(_, Seq(scType)) => checkType(scType)
                  case _ =>
                }
              case Some(o: ScObject) => //do nothing
              case Some(clazz: ScTypeDefinition) =>
                checkTypeProjection(tp)
                ScalaPsiUtil.getCompanionModule(clazz) match {
                  case Some(o: ScObject) if ResolveUtils.isAccessible(o, place, forCompletion = true) && ScalaPsiUtil.hasStablePath(o) => checkObject(o)
                  case _ => //do nothing
                }
              case Some(p: PsiClass) if ResolveUtils.isAccessible(p, place, forCompletion = true) =>
                p.getAllMethods.foreach(method => {
                  if (method.hasModifierProperty("static") && ResolveUtils.isAccessible(method, place, forCompletion = true)) {
                    val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(method), isClassName = true,
                      isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).head
                    lookup.addLookupStrings(p.getName + "." + method.getName)
                    applyVariant(lookup)
                  }
                })
                p.getFields.foreach(field => {
                  if (field.hasModifierProperty("static") && ResolveUtils.isAccessible(field, place, forCompletion = true)) {
                    val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(field), isClassName = true,
                      isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).head
                    lookup.addLookupStrings(p.getName + "." + field.getName)
                    applyVariant(lookup)
                  }
                })
              case _ => checkTypeProjection(tp)
            }
          }
          checkType(tp)
        }
        variants.foreach(applyVariant(_, checkForSecondCompletion = true))
        if (typez.exists(_.equiv(types.Boolean))) {
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
                t.getTypeWithProjections(TypingContext.empty, thisProjections = true) match {
                  case Success(scType, _) =>
                    import org.jetbrains.plugins.scala.lang.psi.types.Nothing
                    val lookupString = (if (foundClazz) t.name + "." else "") + "this"
                    val el = new ScalaLookupItem(t, lookupString)
                    if (!scType.equiv(Nothing) && typez.exists(scType conforms _)) {
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
      case _ => variants.foreach(applyVariant(_, checkForSecondCompletion = true))
    }
  }


  /*
    ref = expr
    expr = ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScAssignStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      val (ref, assign) = extractReference[ScAssignStmt](element)
      if (assign.getRExpression.contains(ref)) {
        assign.getLExpression match {
          case call: ScMethodCall => //todo: it's update method
          case leftExpression: ScExpression =>
            //we can expect that the type is same for left and right parts.
            acceptTypes(ref.expectedTypes(), ref.getVariants, result,
              ref.getResolveScope, parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
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
      val element = positionFromParameters(parameters)
      val (ref, _) = extractReference[PsiElement](element)
      acceptTypes(ref.expectedType().toList.toSeq, ref.getVariants, result, ref.getResolveScope,
        parameters.getInvocationCount > 1, ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
    return ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScReturnStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      val (ref, _) = extractReference[ScReturnStmt](element)
      val fun: ScFunction = PsiTreeUtil.getContextOfType(ref, classOf[ScFunction])
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
        case _ => null
      }
      val actualParams = params(expected)
      if (actualParams != null) {
        val params = actualParams match {
          case Seq(ScTupleType(types)) if braceArgs => types
          case _ => actualParams
        }
        val presentableParams = params.map(_.removeAbstracts)
        val anonFunRenderer = new LookupElementRenderer[LookupElement] {
          def renderElement(element: LookupElement, presentation: LookupElementPresentation) {
            val arrowText = ScalaPsiUtil.functionArrow(referenceExpression.getProject)
            val text = ScalaCompletionUtil.generateAnonymousFunctionText(braceArgs, presentableParams, canonical = false,
              arrowText = arrowText)
            presentation match {
              case realPresentation: RealLookupElementPresentation =>
                if (!realPresentation.hasEnoughSpaceFor(text, false)) {
                  var prefixIndex = presentableParams.length - 1
                  val suffix = s", ... $arrowText"
                  var end = false
                  while (prefixIndex > 0 && !end) {
                    val prefix = ScalaCompletionUtil.generateAnonymousFunctionText(braceArgs,
                      presentableParams.slice(0, prefixIndex), canonical = false, withoutEnd = true,
                      arrowText = arrowText)
                    if (realPresentation.hasEnoughSpaceFor(prefix + suffix, false)) {
                      presentation.setItemText(prefix + suffix)
                      end = true
                    } else prefixIndex -= 1
                  }
                  if (!end) {
                    presentation.setItemText(s"... $arrowText ")
                  }
                } else presentation.setItemText(text)
                presentation.setIcon(Icons.LAMBDA)
              case _ =>
                presentation.setItemText(text)
            }
          }
        }
        val builder = LookupElementBuilder.create("")
                .withRenderer(anonFunRenderer)
                .withInsertHandler(new ScalaGenerateAnonymousFunctionInsertHandler(params, braceArgs))
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
      val element = positionFromParameters(parameters)
      val (referenceExpression, args) = extractReference[ScArgumentExprList](element)
      argumentsForFunction(args, referenceExpression, result)
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
      val element = positionFromParameters(parameters)
      val referenceExpression = element.getContext.asInstanceOf[ScReferenceExpression]
      val block = referenceExpression.getContext.asInstanceOf[ScBlockExpr]
      val args = block.getContext.asInstanceOf[ScArgumentExprList]
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
      val element = positionFromParameters(parameters)
      val (referenceExpression, _) = extractReference[ScArgumentExprList](element)
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
        val element = positionFromParameters(parameters)
        val (ref, ifStmt) = extractReference[ScIfStmt](element)
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
        val element = positionFromParameters(parameters)
        val (ref, whileStmt) = extractReference[ScWhileStmt](element)
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
        val element = positionFromParameters(parameters)
        val (ref, doStmt) = extractReference[ScDoStmt](element)
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
        val element = positionFromParameters(parameters)
        val (ref, infix) = extractReference[ScInfixExpr](element)
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
      val element = positionFromParameters(parameters)
      val (ref, _) = extractReference[ScTryBlock](element)
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
      val element = positionFromParameters(parameters)
      val (ref, _) = extractReference[ScBlockExpr](element)
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
      val element = positionFromParameters(parameters)
      val (ref, _) = extractReference[ScFinallyBlock](element)
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
   inside anonymous function
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      val (ref, _) = extractReference[ScFunctionExpr](element)
      acceptTypes(ref.expectedType().toList.toSeq, ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
   for function definitions
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionDefinition]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      val (ref, _) = extractReference[ScFunctionDefinition](element)
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  /*
   for default parameters
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScParameter]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = positionFromParameters(parameters)
      val (ref, _) = extractReference[ScParameter](element)
      acceptTypes(ref.expectedTypes(), ref.getVariants, result, ref.getResolveScope, parameters.getInvocationCount > 1,
        ScalaCompletionUtil.completeThis(ref), element, parameters.getOriginalPosition)
    }
  })

  extend(CompletionType.SMART, afterNewPattern, new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = positionFromParameters(parameters)

        val refElement = ScalaPsiUtil.getContextOfType(element, false, classOf[ScReferenceElement])

        val renamesMap = new mutable.HashMap[String, (String, PsiNamedElement)]()
        val reverseRenamesMap = new mutable.HashMap[String, PsiNamedElement]()

        refElement match {
          case ref: PsiReference => ref.getVariants.foreach {
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

        val addedClasses = new mutable.HashSet[String]
        val newExpr = PsiTreeUtil.getContextOfType(element, classOf[ScNewTemplateDefinition])
        val types: Array[ScType] = newExpr.expectedTypes().map {
          case ScAbstractType(_, lower, upper) => upper
          case tp => tp
        }
        implicit val typeSystem = newExpr.typeSystem
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
  def extractReference[T <: PsiElement](element: PsiElement): (ScReferenceExpression, T) = {
    val reference = element.getContext.asInstanceOf[ScReferenceExpression]
    val parent = reference.getContext
    parent match {
      case refExpr: ScReferenceExpression =>
        (refExpr, parent.getContext.asInstanceOf[T])
      case _ =>
        (reference, parent.asInstanceOf[T])
    }
  }

  def superParentPattern(clazz: java.lang.Class[_ <: PsiElement]): ElementPattern[PsiElement] = {
    StandardPatterns.or(PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceExpression]).
      withSuperParent(2, clazz),
      PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceExpression]).
        withSuperParent(2, classOf[ScReferenceExpression]).withSuperParent(3, clazz))
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