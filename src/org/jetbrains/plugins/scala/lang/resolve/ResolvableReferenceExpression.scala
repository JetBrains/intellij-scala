package org.jetbrains.plugins.scala
package lang
package resolve

import _root_.com.intellij.openapi.progress.ProgressManager
import processor._
import psi.implicits.ScImplicitlyConvertible
import psi.api.toplevel.imports.usages.ImportUsed
import psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import psi.types.Compatibility.Expression
import psi.api.base.types.{ScSelfTypeElement, ScTypeElement}
import psi.api.base.{ScPrimaryConstructor, ScConstructor}
import com.intellij.psi._
import impl.source.resolve.ResolveCache
import psi.ScalaPsiUtil
import collection.mutable.ArrayBuffer
import psi.fake.FakePsiMethod
import psi.api.statements.params.{ScParameters, ScParameter}
import psi.api.toplevel.templates.{ScTemplateBody, ScExtendsBlock}
import psi.api.toplevel.ScTypedDefinition
import util.PsiModificationTracker
import caches.CachesUtil
import psi.types.result.{Success, TypingContext}
import psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import psi.types._
import extensions.toPsiNamedElementExt
import psi.api.toplevel.typedef.ScTemplateDefinition
import annotation.tailrec
import annotation.tailrec

trait ResolvableReferenceExpression extends ScReferenceExpression {
  private object Resolver extends ReferenceExpressionResolver(false)
  private object ShapesResolver extends ReferenceExpressionResolver(true)

  def multiResolve(incomplete: Boolean): Array[ResolveResult] = {
    if (resolveFunction != null) return resolveFunction()
    ResolveCache.getInstance(getProject).resolveWithCaching(this, Resolver, true, incomplete)
  }

  def shapeResolve: Array[ResolveResult] = {
    ProgressManager.checkCanceled()
    if (shapeResolveFunction != null) return shapeResolveFunction()
    CachesUtil.getWithRecursionPreventing(this, CachesUtil.REF_EXPRESSION_SHAPE_RESOLVE_KEY,
      new CachesUtil.MyProvider(this, (expr: ResolvableReferenceExpression) => expr.shapeResolveInner)
      (PsiModificationTracker.MODIFICATION_COUNT), Array.empty[ResolveResult])
  }

  private def shapeResolveInner: Array[ResolveResult] = {
    ShapesResolver.resolve(this, incomplete = false)
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
    ref.qualifier match {
      case None => resolveUnqalified(ref, processor)
      case Some(superQ : ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
      case Some(q) => processTypes(q, processor)
    }
    val res = processor.rrcandidates
    if (accessibilityCheck && res.length == 0) return doResolve(ref, processor, accessibilityCheck = false)
    res
  }

  private def resolveUnqalified(ref: ResolvableReferenceExpression, processor: BaseProcessor) {
    ref.getContext match {
      case inf: ScInfixExpr if ref == inf.operation => {
        val thisOp = if (ref.rightAssoc) inf.rOp else inf.lOp
        processTypes(thisOp, processor)
      }
      case postf: ScPostfixExpr if ref == postf.operation => processTypes(postf.operand, processor)
      case pref: ScPrefixExpr if ref == pref.operation => processTypes(pref.operand, processor)
      case _ => resolveUnqualifiedExpression(ref, processor)
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
      case args: ScArgumentExprList => {
        args.callReference match {
          case Some(callReference) =>
            processAnyAssignment(args.exprs, callReference, args.invocationCount, ref, assign, processor)
          case None => processConstructorReference(args, ref, assign, processor)
        }
      }
      case tuple: ScTuple => tuple.getContext match {
        case inf: ScInfixExpr if inf.getArgExpr == tuple =>
          processAnyAssignment(tuple.exprs, inf.operation, 1, ref, assign, processor)
        case _ =>
      }
      case p: ScParenthesisedExpr => p.getContext match {
        case inf: ScInfixExpr if inf.getArgExpr == p =>
          processAnyAssignment(p.expr.toSeq, inf.operation, 1, ref, assign, processor)
        case _ =>
      }
      case _ =>
    }
  }

  def processAnyAssignment(exprs: Seq[ScExpression], callReference: ScReferenceExpression, invocationCount: Int,
                             ref: ResolvableReferenceExpression, assign: PsiElement, processor: BaseProcessor) {
    for (variant <- callReference.multiResolve(false)) {
      def processResult(r: ScalaResolveResult) = r match {
        case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) => {
          if (!processor.isInstanceOf[CompletionProcessor])
            fun.getParamByName(ref.refName, invocationCount - 1) match { //todo: why -1?
              case Some(param) => processor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst).
                      put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
              case None =>
            }
          else {
            //for completion only!
            funCollectNamedCompletions(fun.paramClauses, assign, processor, subst, exprs, invocationCount)
          }
        }
        case ScalaResolveResult(fun: FakePsiMethod, subst: ScSubstitutor) => //todo: ?
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
    args.getContext match {
      case constr: ScConstructor => {
        val te: ScTypeElement = constr.typeElement
        val tp: ScType = te.getType(TypingContext.empty).getOrElse(return)
        val typeArgs: Seq[ScTypeElement] = constr.typeArgList.map(_.typeArgs).getOrElse(Seq())
        ScType.extractClassType(tp) match {
          case Some((clazz, subst)) if !clazz.isInstanceOf[ScTemplateDefinition] && clazz.isAnnotationType => {
            if (!baseProcessor.isInstanceOf[CompletionProcessor]) {
              for (method <- clazz.getMethods) {
                method match {
                  case p: PsiAnnotationMethod => {
                    if (ScalaPsiUtil.memberNamesEquals(p.name, ref.refName)) {
                      baseProcessor.execute(p, ResolveState.initial)
                    }
                  }
                  case _ =>
                }
              }
            } else {
              if (args.invocationCount == 1) {
                val methods: ArrayBuffer[PsiAnnotationMethod] = new ArrayBuffer[PsiAnnotationMethod] ++
                  clazz.getMethods.toSeq.flatMap(f => f match {case f: PsiAnnotationMethod => Seq(f) case _ => Seq.empty})
                val exprs = args.exprs
                var i = 0
                def tail() {
                  if (!methods.isEmpty) methods.remove(0)
                }
                while (exprs(i) != assign) {
                  exprs(i) match {
                    case assignStmt: ScAssignStmt => {
                      assignStmt.getLExpression match {
                        case ref: ScReferenceExpression => {
                          val ind = methods.indexWhere(p => ScalaPsiUtil.memberNamesEquals(p.name, ref.refName))
                          if (ind != -1) methods.remove(ind)
                          else tail()
                        }
                        case _ => tail()
                      }
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
          }
          case Some((clazz, subst)) => {
            val processor: MethodResolveProcessor = new MethodResolveProcessor(constr, "this",
              constr.arguments.toList.map(_.exprs.map(Expression(_))), typeArgs, Seq.empty /* todo: ? */,
              constructorResolve = true, enableTupling = true)
            val state = ResolveState.initial.put(ScSubstitutor.key, subst)
            for (constr <- clazz.getConstructors) {
              processor.execute(constr, state)
            }
            for (candidate <- processor.candidatesS) {
              candidate match {
                case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) => {
                  if (!baseProcessor.isInstanceOf[CompletionProcessor])
                    fun.getParamByName(ref.refName, constr.arguments.indexOf(args)) match {
                      case Some(param) =>
                        baseProcessor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst).
                          put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
                      case None =>
                    }
                  else {
                    //for completion only!
                    funCollectNamedCompletions(fun.paramClauses, assign, baseProcessor, subst, args.exprs, args.invocationCount)
                  }
                }
                case ScalaResolveResult(constructor: ScPrimaryConstructor, _) => {
                  if (!baseProcessor.isInstanceOf[CompletionProcessor])
                    constructor.getParamByName(ref.refName, constr.arguments.indexOf(args)) match {
                      case Some(param) =>
                        baseProcessor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst).
                          put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
                      case None =>
                    }
                  else {
                    //for completion only!
                    funCollectNamedCompletions(constructor.parameterList, assign, baseProcessor, subst, args.exprs, args.invocationCount)
                  }
                }
                case _ =>
              }
            }
          }
          case _ =>
        }
      }
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
        if (!params.isEmpty) params.remove(0)
      }
      while (exprs(i) != assign) {
        exprs(i) match {
          case assignStmt: ScAssignStmt => {
            assignStmt.getLExpression match {
              case ref: ScReferenceExpression => {
                val ind = params.indexWhere(p => ScalaPsiUtil.memberNamesEquals(p.name, ref.refName))
                if (ind != -1) params.remove(ind)
                else tail()
              }
              case _ => tail()
            }
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

  private def processTypes(e: ScExpression, processor: BaseProcessor) {
    ProgressManager.checkCanceled()
    //first of all, try nonValueType.internalType
    val nonValueType = e.getNonValueType(TypingContext.empty)
    nonValueType match {
      case Success(ScTypePolymorphicType(internal, tp), _) if !tp.isEmpty &&
        !internal.isInstanceOf[ScMethodType] && !internal.isInstanceOf[ScUndefinedType] /* optimization */ =>
        processType(internal, e, processor)
        if (!processor.candidates.isEmpty) return
      case _ =>
    }
    //if it's ordinar case
    val result = e.getType(TypingContext.empty)
    if (result.isDefined) {
      processType(result.get, e, processor)
    }
  }

  private def processType(aType: ScType, e: ScExpression, processor: BaseProcessor) {
    val shape = processor match {
      case m: MethodResolveProcessor => m.isShapeResolve
      case _ => false
    }

    val fromType = e match {
      case ref: ScReferenceExpression => ref.bind() match {
        case Some(ScalaResolveResult(self: ScSelfTypeElement, _)) => aType
        case Some(r@ScalaResolveResult(b: ScTypedDefinition, subst)) if b.isStable =>
          r.fromType match {
            case Some(fT) => ScProjectionType(fT, b, ScSubstitutor.empty, superReference = false)
            case None => ScType.designator(b)
          }
        case _ => aType
      }
      case _ => aType
    }
    processor.processType(aType, e, ResolveState.initial.put(BaseProcessor.FROM_TYPE_KEY, fromType))

    val candidates = processor.candidatesS

    aType match {
      case d: ScDesignatorType if d.isStatic => return
      case ScDesignatorType(p: PsiPackage) => return
      case _ =>
    }

    if (candidates.size == 0 || (!shape && candidates.forall(!_.isApplicable)) ||
            (processor.isInstanceOf[CompletionProcessor] &&
            processor.asInstanceOf[CompletionProcessor].collectImplicits)) {
      processor match {
        case rp: ResolveProcessor =>
          rp.resetPrecedence() //do not clear candidate set, we want wrong resolve, if don't found anything
        case _ =>
      }
      val noImplicitsForArgs = candidates.size > 0
      collectImplicits(e, processor, noImplicitsForArgs)
    }
  }

  private def collectImplicits(e: ScExpression, processor: BaseProcessor, noImplicitsForArgs: Boolean) {
    processor match {
      case _: CompletionProcessor => {
        for ((t, fun, importsUsed, _) <- e.implicitMap()._1) { //todo: args?
          ProgressManager.checkCanceled()
          var state = ResolveState.initial.put(ImportUsed.key, importsUsed)
          state = state.put(CachesUtil.IMPLICIT_FUNCTION, fun).put(CachesUtil.IMPLICIT_TYPE, t)
          e.getClazzForType(t) match {
            case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
            case _ =>
          }
          processor.processType(t, e, state)
        }
        return
      }
      case m: MethodResolveProcessor => m.noImplicitsForArgs = true
      case _ =>
    }
    val name = processor match {
      case rp: ResolveProcessor => rp.name // See SCL-2934.
      case _ => refName
    }
    val (t: ScType, fun: PsiNamedElement, importsUsed: collection.Set[ImportUsed], _) =
      ScalaPsiUtil.findImplicitConversion(e, name, processor.kinds, this, processor, noImplicitsForArgs) match {
        case Some(a) => a
        case None => return
      }
    ProgressManager.checkCanceled()
    var state = ResolveState.initial.put(ImportUsed.key, importsUsed)
    state = state.put(CachesUtil.IMPLICIT_FUNCTION, fun).put(CachesUtil.IMPLICIT_TYPE, t)
    e.getClazzForType(t) match {
      case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
      case _ =>
    }
    state = state.put(BaseProcessor.FROM_TYPE_KEY, t)
    processor.processType(t, e, state)
  }
}