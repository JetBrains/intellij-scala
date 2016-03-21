package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedMappedWithRecursionGuard, CachedWithRecursionGuard, ModCount}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

trait ResolvableReferenceExpression extends ScReferenceExpression {
  private object Resolver extends ReferenceExpressionResolver(false)
  private object ShapesResolver extends ReferenceExpressionResolver(true)

  @CachedMappedWithRecursionGuard(this, Array.empty, ModCount.getBlockModificationCount)
  def multiResolveImpl(incomplete: Boolean): Array[ResolveResult] = Resolver.resolve(ResolvableReferenceExpression.this, incomplete)

  def multiResolve(incomplete: Boolean): Array[ResolveResult] = {
    if (resolveFunction != null) resolveFunction()
    else multiResolveImpl(incomplete)
  }

  @CachedWithRecursionGuard[ResolvableReferenceExpression](this, Array.empty[ResolveResult], ModCount.getBlockModificationCount)
  private def shapeResolveImpl: Array[ResolveResult] = ShapesResolver.resolve(this, incomplete = false)

  def shapeResolve: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    if (shapeResolveFunction != null) shapeResolveFunction()
    else shapeResolveImpl
  }

  def isAssignmentOperator = {
    val context = getContext
    (context.isInstanceOf[ScInfixExpr] || context.isInstanceOf[ScMethodCall]) &&
            refName.endsWith("=") &&
            !(refName.startsWith("=") || Seq("!=", "<=", ">=").contains(refName) || refName.exists(_.isLetterOrDigit))
  }

  def isUnaryOperator = {
    getContext match {
      case pref: ScPrefixExpr if pref.operation == this => true
      case _ => false
    }
  }

  def rightAssoc = refName.endsWith(":")

  def doResolve(ref: ResolvableReferenceExpression, processor: BaseProcessor,
                accessibilityCheck: Boolean = true): Array[ResolveResult] = {
    if (!accessibilityCheck) processor.doNotCheckAccessibility()
    val actualProcessor = ref.qualifier match {
      case None =>
        resolveUnqalified(ref, processor)
      case Some(superQ : ScSuperReference) =>
        ResolveUtils.processSuperReference(superQ, processor, this)
        processor
      case Some(q) =>
        processTypes(ref, q, processor)
    }
    val res = actualProcessor.rrcandidates
    if (accessibilityCheck && res.length == 0) return doResolve(ref, processor, accessibilityCheck = false)
    res
  }
  private def resolveUnqalified(ref: ResolvableReferenceExpression, processor: BaseProcessor): BaseProcessor = {
    ref.getContext match {
      case inf: ScInfixExpr if ref == inf.operation =>
        val thisOp = if (ref.rightAssoc) inf.rOp else inf.lOp
        processTypes(ref, thisOp, processor)
      case postf: ScPostfixExpr if ref == postf.operation => processTypes(ref, postf.operand, processor)
      case pref: ScPrefixExpr if ref == pref.operation => processTypes(ref, pref.operand, processor)
      case _ =>
        resolveUnqualifiedExpression(ref, processor)
        processor
    }
  }

  private def resolveUnqualifiedExpression(ref: ResolvableReferenceExpression, processor: BaseProcessor) {
    @tailrec
    def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
      if (place == null) return
      if (!place.processDeclarations(processor,
        ResolveState.initial(),
        lastParent, ref)) return
      place match {
        case (_: ScTemplateBody | _: ScExtendsBlock) => //template body and inherited members are at the same level
        case _ => if (!processor.changedLevel) return
      }
      treeWalkUp(place.getContext, place)
    }
    val context = ref.getContext
    val contextElement = (context, processor) match {
      case (x: ScAssignStmt, _ ) if x.getLExpression == ref => Some(context)
      case (_, _: CompletionProcessor) => Some(ref)
      case _ => None
    }

    contextElement match {
      case Some(assign) => processAssignment(assign, ref, processor)
      case _ =>
    }
    treeWalkUp(ref, null)
  }

  private def processAssignment(assign: PsiElement, ref: ResolvableReferenceExpression, processor: BaseProcessor) {
    assign.getContext match { //trying to resolve naming parameter
      case args: ScArgumentExprList =>
        args.callReference match {
          case Some(callReference) if args.getContext.isInstanceOf[MethodInvocation] =>
            processAnyAssignment(args.exprs, args.getContext.asInstanceOf[MethodInvocation], callReference,
              args.invocationCount, ref, assign, processor)
          case None => processConstructorReference(args, ref, assign, processor)
        }
      case tuple: ScTuple => tuple.getContext match {
        case inf: ScInfixExpr if inf.getArgExpr == tuple =>
          processAnyAssignment(tuple.exprs, inf, inf.operation, 1, ref, assign, processor)
        case _ =>
      }
      case p: ScParenthesisedExpr => p.getContext match {
        case inf: ScInfixExpr if inf.getArgExpr == p =>
          processAnyAssignment(p.expr.toSeq, inf, inf.operation, 1, ref, assign, processor)
        case _ =>
      }
      case _ =>
    }
  }

  def processAnyAssignment(exprs: Seq[ScExpression], call: MethodInvocation, callReference: ScReferenceExpression, invocationCount: Int,
                             ref: ResolvableReferenceExpression, assign: PsiElement, processor: BaseProcessor) {
    for (variant <- callReference.multiResolve(false)) {
      def processResult(r: ScalaResolveResult) = r match {
        case ScalaResolveResult(fun: ScFunction, subst) if r.isDynamic &&
          fun.name == ResolvableReferenceExpression.APPLY_DYNAMIC_NAMED =>
          //add synthetic parameter
          if (!processor.isInstanceOf[CompletionProcessor]) {
            val state: ResolveState = ResolveState.initial().put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE)
            processor.execute(ScalaPsiElementFactory.createParameterFromText(ref.refName + ": Any", getManager), state)
          }
        case ScalaResolveResult(named, subst) if call.applyOrUpdateElement.exists(_.isDynamic) &&
          call.applyOrUpdateElement.get.name == ResolvableReferenceExpression.APPLY_DYNAMIC_NAMED =>
          //add synthetic parameter
          if (!processor.isInstanceOf[CompletionProcessor]) {
            val state: ResolveState = ResolveState.initial().put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE)
            processor.execute(ScalaPsiElementFactory.createParameterFromText(ref.refName + ": Any", getManager), state)
          }
        case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) =>
          if (!processor.isInstanceOf[CompletionProcessor]) {
            fun.getParamByName(ref.refName, invocationCount - 1) match { //todo: why -1?
              case Some(param) =>
                var state = ResolveState.initial.put(ScSubstitutor.key, subst).
                  put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE)
                if (!ScalaPsiUtil.memberNamesEquals(param.name, ref.refName)) {
                  state = state.put(ResolverEnv.nameKey, param.deprecatedName.get)
                }
                processor.execute(param, state)
              case None =>
            }
          } else {
            //for completion only!
            funCollectNamedCompletions(fun.paramClauses, assign, processor, subst, exprs, invocationCount)
          }
        case ScalaResolveResult(fun: FakePsiMethod, subst: ScSubstitutor) => //todo: ?
        case ScalaResolveResult(method: PsiMethod, subst) => 
          assign.getContext match {
            case args: ScArgumentExprList =>
              args.getContext match {
                case methodCall: ScMethodCall if methodCall.isNamedParametersEnabledEverywhere =>
                  method.getParameterList.getParameters foreach {
                    p => processor.execute(p, ResolveState.initial().put(ScSubstitutor.key, subst).
                         put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
                  }
                case _ =>
              }
            case _ => 
          }
        case _ =>
      }
      variant match {
        case x: ScalaResolveResult =>
          processResult(x)
          // Consider named parameters of apply method; see SCL-2407
          x.innerResolveResult.foreach(processResult)
        case _ =>
      }
    }
  }

  private def processConstructorReference(args: ScArgumentExprList, ref: ResolvableReferenceExpression,
                                          assign: PsiElement, baseProcessor: BaseProcessor) {
    def processConstructor(elem: PsiElement, tp: ScType, typeArgs: Seq[ScTypeElement], arguments: Seq[ScArgumentExprList],
                           secondaryConstructors: (ScClass) => Seq[ScFunction]) {
      tp.extractClassType() match {
        case Some((clazz, subst)) if !clazz.isInstanceOf[ScTemplateDefinition] && clazz.isAnnotationType =>
          if (!baseProcessor.isInstanceOf[CompletionProcessor]) {
            for (method <- clazz.getMethods) {
              method match {
                case p: PsiAnnotationMethod =>
                  if (ScalaPsiUtil.memberNamesEquals(p.name, ref.refName)) {
                    baseProcessor.execute(p, ResolveState.initial)
                  }
                case _ =>
              }
            }
          } else {
            if (args.invocationCount == 1) {
              val methods: ArrayBuffer[PsiAnnotationMethod] = new ArrayBuffer[PsiAnnotationMethod] ++
                clazz.getMethods.toSeq.flatMap {
                  case f: PsiAnnotationMethod => Seq(f)
                  case _ => Seq.empty
                }
              val exprs = args.exprs
              var i = 0
              def tail() {
                if (methods.nonEmpty) methods.remove(0)
              }
              while (exprs(i) != assign) {
                exprs(i) match {
                  case assignStmt: ScAssignStmt =>
                    assignStmt.getLExpression match {
                      case ref: ScReferenceExpression =>
                        val ind = methods.indexWhere(p => ScalaPsiUtil.memberNamesEquals(p.name, ref.refName))
                        if (ind != -1) methods.remove(ind)
                        else tail()
                      case _ => tail()
                    }
                  case _ => tail()
                }
                i = i + 1
              }
              for (method <- methods) {
                baseProcessor.execute(method, ResolveState.initial.put(ScSubstitutor.key, subst).
                  put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
              }
            }
          }
        case Some((clazz, subst)) =>
          val processor: MethodResolveProcessor = new MethodResolveProcessor(elem, "this",
            arguments.toList.map(_.exprs.map(Expression(_))), typeArgs, Seq.empty /* todo: ? */ ,
            constructorResolve = true, enableTupling = true)
          val state = ResolveState.initial.put(ScSubstitutor.key, subst)
          clazz match {
            case clazz: ScClass =>
              for (constr <- secondaryConstructors(clazz)) {
                processor.execute(constr, state)
              }
              clazz.constructor.foreach(processor.execute(_, state))
            case _ =>
              for (constr <- clazz.getConstructors) {
                processor.execute(constr, state)
              }
          }
          for (candidate <- processor.candidatesS) {
            candidate match {
              case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) =>
                if (!baseProcessor.isInstanceOf[CompletionProcessor]) {
                  fun.getParamByName(ref.refName, arguments.indexOf(args)) match {
                    case Some(param) =>
                      var state = ResolveState.initial.put(ScSubstitutor.key, subst).
                        put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE)
                      if (!ScalaPsiUtil.memberNamesEquals(param.name, ref.refName)) {
                        state = state.put(ResolverEnv.nameKey, param.deprecatedName.get)
                      }
                      baseProcessor.execute(param, state)
                    case None =>
                  }
                } else {
                  //for completion only!
                  funCollectNamedCompletions(fun.paramClauses, assign, baseProcessor, subst, args.exprs, args.invocationCount)
                }
              case ScalaResolveResult(constructor: ScPrimaryConstructor, _) =>
                if (!baseProcessor.isInstanceOf[CompletionProcessor])
                  constructor.getParamByName(ref.refName, arguments.indexOf(args)) match {
                    case Some(param) =>
                      baseProcessor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst).
                        put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
                    case None =>
                  }
                else {
                  //for completion only!
                  funCollectNamedCompletions(constructor.parameterList, assign, baseProcessor, subst, args.exprs, args.invocationCount)
                }
              case _ =>
            }
          }
        case _ =>
      }
    }
    args.getContext match {
      case s: ScSelfInvocation =>
        val clazz = ScalaPsiUtil.getContextOfType(s, true, classOf[ScClass])
        if (clazz == null) return
        val tp: ScType = clazz.asInstanceOf[ScClass].getType(TypingContext.empty).getOrElse(return)
        val typeArgs: Seq[ScTypeElement] = Seq.empty
        val arguments = s.arguments
        val secondaryConstructors = (c: ScClass) => {
          if (c != clazz) Seq.empty
          else {
            c.secondaryConstructors.filter(f =>
              !PsiTreeUtil.isContextAncestor(f, s, true) &&
                f.getTextRange.getStartOffset < s.getTextRange.getStartOffset
            )
          }
        }
        processConstructor(s, tp, typeArgs, arguments, secondaryConstructors)
      case constr: ScConstructor =>
        val tp: ScType = constr.typeElement.getType(TypingContext.empty).getOrElse(return)
        val typeArgs: Seq[ScTypeElement] = constr.typeArgList.map(_.typeArgs).getOrElse(Seq())
        val arguments = constr.arguments
        val secondaryConstructors = (clazz: ScClass) => clazz.secondaryConstructors
        processConstructor(constr, tp, typeArgs, arguments, secondaryConstructors)
      case _ =>
    }
  }

  private def funCollectNamedCompletions(clauses: ScParameters, assign: PsiElement, processor: BaseProcessor,
                                         subst: ScSubstitutor, exprs: Seq[ScExpression], invocationCount: Int) {
    if (clauses.clauses.length >= invocationCount) {
      val actualClause = clauses.clauses(invocationCount - 1)
      val params = new ArrayBuffer[ScParameter] ++ actualClause.parameters
      var i = 0
      def tail() {
        if (params.nonEmpty) params.remove(0)
      }
      while (exprs(i) != assign) {
        exprs(i) match {
          case assignStmt: ScAssignStmt =>
            assignStmt.getLExpression match {
              case ref: ScReferenceExpression =>
                val ind = params.indexWhere(p => ScalaPsiUtil.memberNamesEquals(p.name, ref.refName))
                if (ind != -1) params.remove(ind)
                else tail()
              case _ => tail()
            }
          case _ => tail()
        }
        i = i + 1
      }
      for (param <- params) {
        processor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst).
                put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
      }
    }
  }

  private def processTypes(reference: ScReferenceExpression, e: ScExpression, processor: BaseProcessor): BaseProcessor = {
    ProgressManager.checkCanceled()
    //first of all, try nonValueType.internalType
    val nonValueType = e.getNonValueType(TypingContext.empty)
    nonValueType match {
      case Success(ScTypePolymorphicType(internal, tp), _) if tp.nonEmpty &&
        !internal.isInstanceOf[ScMethodType] && !internal.isInstanceOf[ScUndefinedType] /* optimization */ =>
        processType(internal, reference, e, processor)
        if (processor.candidates.nonEmpty) return processor
      case _ =>
    }
    //if it's ordinar case
    val result = e.getType(TypingContext.empty)
    if (result.isDefined) {
      processType(result.get, reference, e, processor)
    } else {
      processor
    }
  }

  private def processType(aType: ScType, reference: ScReferenceExpression, e: ScExpression,
                          processor: BaseProcessor): BaseProcessor = {
    val shape = processor match {
      case m: MethodResolveProcessor => m.isShapeResolve
      case _ => false
    }

    val fromType = e match {
      case ref: ScReferenceExpression => ref.bind() match {
        case Some(ScalaResolveResult(self: ScSelfTypeElement, _)) => aType
        case Some(r@ScalaResolveResult(b: ScTypedDefinition, subst)) if b.isStable =>
          r.fromType match {
            case Some(fT) => ScProjectionType(fT, b, superReference = false)
            case None => ScalaType.designator(b)
          }
        case _ => aType
      }
      case _ => aType
    }
    val state: ResolveState = fromType match {
      case ScDesignatorType(p: PsiPackage) => ResolveState.initial()
      case _ => ResolveState.initial.put(BaseProcessor.FROM_TYPE_KEY, fromType)
    }
    processor.processType(aType, e, state)

    val candidates = processor.candidatesS

    aType match {
      case d: ScDesignatorType if d.isStatic => return processor
      case ScDesignatorType(p: PsiPackage) => return processor
      case _ =>
    }

    if (candidates.isEmpty || (!shape && candidates.forall(!_.isApplicable())) ||
            (processor.isInstanceOf[CompletionProcessor] &&
            processor.asInstanceOf[CompletionProcessor].collectImplicits)) {
      processor match {
        case rp: ResolveProcessor =>
          rp.resetPrecedence() //do not clear candidate set, we want wrong resolve, if don't found anything
        case _ =>
      }
      val noImplicitsForArgs = candidates.nonEmpty
      collectImplicits(e, processor, noImplicitsForArgs)

      if (processor.candidates.length == 0)
        return processDynamic(fromType, reference, e, processor)
    }

    processor
  }

  private def processDynamic(aType: ScType, reference: ScReferenceExpression, e: ScExpression,
                             baseProcessor: BaseProcessor): BaseProcessor = {
    val cachedClass = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.Dynamic").orNull
    if (cachedClass == null) return baseProcessor
    val dynamicType = ScDesignatorType(cachedClass)

    if (aType.conforms(dynamicType)) {
      baseProcessor match {
        case mrp: MethodResolveProcessor =>
          val callOption = reference.getContext match {
            case m: MethodInvocation if m.getInvokedExpr ==  reference => Some(m)
            case _ => None
          }
          val argumentExpressions = callOption.map(_.argumentExpressions)
          val emptyStringExpression = ScalaPsiElementFactory.createExpressionFromText("\"\"", e.getManager)
          import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceExpression._
          val name = callOption match {
            case Some(call) => getDynamicNameForMethodInvocation(call)
            case _ =>
              reference.getContext match {
                case a: ScAssignStmt if a.getLExpression == reference => UPDATE_DYNAMIC
                case _ => SELECT_DYNAMIC
              }
          }

          val newProcessor = new MethodResolveProcessor(e, name , List(List(emptyStringExpression),
            argumentExpressions.toSeq.flatten), mrp.typeArgElements, mrp.prevTypeInfo, mrp.kinds, mrp.expectedOption,
            mrp.isUnderscore, mrp.isShapeResolve, mrp.constructorResolve, mrp.noImplicitsForArgs,
            mrp.enableTupling, mrp.selfConstructorResolve, isDynamic = true)

          newProcessor.processType(aType, e, ResolveState.initial.put(BaseProcessor.FROM_TYPE_KEY, aType))

          newProcessor
        case _ => baseProcessor
      }
    } else {
      baseProcessor
    }
  }

  private def collectImplicits(e: ScExpression, processor: BaseProcessor, noImplicitsForArgs: Boolean) {
    processor match {
      case _: CompletionProcessor =>
        val convertible: ScImplicitlyConvertible = new ScImplicitlyConvertible(e)
        for (res <- convertible.implicitMap()) { //todo: args?
          ProgressManager.checkCanceled()
          var state = ResolveState.initial.put(ImportUsed.key, res.importUsed)
          state = state.put(CachesUtil.IMPLICIT_FUNCTION, res.element).put(CachesUtil.IMPLICIT_TYPE, res.tp)
          res.getClazz match {
            case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
            case _ =>
          }
          processor.processType(res.tp, e, state)
        }
        return
      case m: MethodResolveProcessor => m.noImplicitsForArgs = true
      case _ =>
    }
    val name = processor match {
      case rp: ResolveProcessor => rp.name // See SCL-2934.
      case _ => refName
    }
    val res =
      ScalaPsiUtil.findImplicitConversion(e, name, this, processor, noImplicitsForArgs) match {
        case Some(a) => a
        case None => return
      }
    ProgressManager.checkCanceled()
    var state = ResolveState.initial.put(ImportUsed.key, res.importUsed)
    state = state.put(CachesUtil.IMPLICIT_FUNCTION, res.element).put(CachesUtil.IMPLICIT_TYPE, res.tp)
    res.getClazz match {
      case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
      case _ =>
    }
    state = state.put(BaseProcessor.FROM_TYPE_KEY, res.tp)
    state = state.put(BaseProcessor.UNRESOLVED_TYPE_PARAMETERS_KEY, res.unresolvedTypeParameters)
    processor.processType(res.getTypeWithDependentSubstitutor, e, state)
  }
}

object ResolvableReferenceExpression {
  val APPLY_DYNAMIC_NAMED = "applyDynamicNamed"
  val APPLY_DYNAMIC = "applyDynamic"
  val SELECT_DYNAMIC = "selectDynamic"
  val UPDATE_DYNAMIC = "updateDynamic"
  val NAMED = "Named"

  def getDynamicReturn(tp: ScType): ScType = {
    tp match {
      case ScTypePolymorphicType(mt: ScMethodType, typeArgs) => ScTypePolymorphicType(mt.returnType, typeArgs)
      case mt: ScMethodType => mt.returnType
      case _ => tp
    }
  }
  
  def getDynamicNameForMethodInvocation(call: MethodInvocation): String = {
    call.argumentExpressions.find {
      case a: ScAssignStmt =>
        a.getLExpression match {
          case r: ScReferenceExpression if r.qualifier.isEmpty => true
          case _ => false
        }
      case _ => false
    } match {
      case Some(_) => APPLY_DYNAMIC_NAMED
      case _ => APPLY_DYNAMIC
    }
  }
}