package org.jetbrains.plugins.scala
package lang
package resolve

import _root_.com.intellij.openapi.progress.ProgressManager
import _root_.com.intellij.psi.impl.PsiManagerEx
import processor._
import psi.implicits.ScImplicitlyConvertible
import psi.api.toplevel.imports.usages.ImportUsed
import psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import psi.types.Compatibility.Expression
import psi.api.base.types.{ScTypeElement, ScParameterizedTypeElement}
import psi.api.base.{ScPrimaryConstructor, ScConstructor, ScReferenceElement}
import caches.CachesUtil
import com.intellij.psi._
import psi.types.result.TypingContext
import psi.ScalaPsiUtil
import psi.types.{ScDesignatorType, ScSubstitutor, ScType}
import collection.mutable.ArrayBuffer
import psi.fake.FakePsiMethod
import psi.api.statements.params.{ScParameters, ScParameter}

trait ResolvableReferenceExpression extends ScReferenceExpression {
  private object Resolver extends ReferenceExpressionResolver(this, false)
  private object ShapesResolver extends ReferenceExpressionResolver(this, true)
  
  def multiResolve(incomplete: Boolean) = {
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, Resolver, true, incomplete)
  }

  @volatile
  private var shapeResolveResults: Array[ResolveResult] = null
  @volatile
  private var shapeResolveResultsModCount: Long = 0

  def shapeResolve: Array[ResolveResult] = {
    ProgressManager.checkCanceled
    var tp = shapeResolveResults
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && shapeResolveResultsModCount == curModCount) {
      return tp
    }
    tp = shapeResolveInner
    shapeResolveResults = tp
    shapeResolveResultsModCount = curModCount
    return tp
  }

  private def shapeResolveInner: Array[ResolveResult] = {
    ShapesResolver.resolve(this, false)
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

  def doResolve(ref: ResolvableReferenceExpression, processor: BaseProcessor): Array[ResolveResult] = {
    ref.qualifier match {
      case None => resolveUnqalified(ref, processor)
      case Some(superQ : ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
      case Some(q) => processTypes(q, processor)
    }
    processor.candidates
  }

  private def resolveUnqalified(ref: ResolvableReferenceExpression, processor: BaseProcessor) = {
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

  private def resolveUnqualifiedExpression(ref: ResolvableReferenceExpression, processor: BaseProcessor) = {
    def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
      place match {
        case null =>
        case p => {
          if (!p.processDeclarations(processor,
            ResolveState.initial(),
            lastParent, ref)) return
          if (!processor.changedLevel) return
          treeWalkUp(place.getContext, place)
        }
      }
    }
    val context = ref.getContext
    (if (context.isInstanceOf[ScAssignStmt] && context.asInstanceOf[ScAssignStmt].getLExpression == ref) Some(context)
    else if (processor.isInstanceOf[CompletionProcessor]) Some(ref)
    else None) match {
      case Some(assign) => processAssignment(assign, ref, processor)
      case _ =>
    }
    treeWalkUp(ref, null)
  }

  private def processAssignment(assign: PsiElement, ref: ResolvableReferenceExpression, processor: BaseProcessor) {
    assign.getContext match { //trying to resolve naming parameter
      case args: ScArgumentExprList => {
        args.callReference match {
          case Some(callReference) => processCallReference(args, callReference, ref, assign, processor)
          case None => processConstructorReference(args, ref, assign, processor)
        }
      }
      case _ =>
    }
  }

  private def processConstructorReference(args: ScArgumentExprList, ref: ResolvableReferenceExpression,
                                          assign: PsiElement, baseProcessor: BaseProcessor): Unit = {
    args.getContext match {
      case constr: ScConstructor => {
        val te: ScTypeElement = constr.typeElement
        val tp: ScType = te.getType(TypingContext.empty).getOrElse(return)
        val typeArgs: Seq[ScTypeElement] = te match {
          case p: ScParameterizedTypeElement => p.typeArgList.typeArgs
          case _ => Seq.empty[ScTypeElement]
        }
        ScType.extractClassType(tp) match {
          case Some((clazz, subst)) => {
            val processor: MethodResolveProcessor = new MethodResolveProcessor(constr, "this",
              constr.arguments.toList.map(_.exprs.map(Expression(_))), typeArgs, constructorResolve = true)
            val state = ResolveState.initial.put(ScSubstitutor.key, subst)
            for (constr <- clazz.getConstructors) {
              processor.execute(constr, state)
            }
            for (candidate <- processor.candidates) {
              candidate match {
                case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) => {
                  if (!baseProcessor.isInstanceOf[CompletionProcessor])
                    fun.getParamByName(ref.refName, constr.arguments.indexOf(args)) match {
                      case Some(param) => baseProcessor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst))
                      case None =>
                    }
                  else {
                    //for completion only!
                    funCollectNamedCompletions(fun.paramClauses, args, assign, baseProcessor, subst)
                  }
                }
                case ScalaResolveResult(constructor: ScPrimaryConstructor, _) => {
                  if (!baseProcessor.isInstanceOf[CompletionProcessor])
                    constructor.getParamByName(ref.refName, constr.arguments.indexOf(args)) match {
                      case Some(param) => baseProcessor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst))
                      case None =>
                    }
                  else {
                    //for completion only!
                    funCollectNamedCompletions(constructor.parameterList, args, assign, baseProcessor, subst)
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

  private def funCollectNamedCompletions(clauses: ScParameters, args: ScArgumentExprList, assign: PsiElement,
                                         processor: BaseProcessor, subst: ScSubstitutor): Unit = {
    if (clauses.clauses.length >= args.invocationCount) {
      val actualClause = clauses.clauses(args.invocationCount - 1)
      val params = new ArrayBuffer[ScParameter] ++ actualClause.parameters
      val exprs = args.exprs
      var i = 0
      def tail: Unit = if (!params.isEmpty) params.remove(0)
      while (exprs(i) != assign) {
        exprs(i) match {
          case assignStmt: ScAssignStmt => {
            assignStmt.getLExpression match {
              case ref: ScReferenceExpression => {
                val ind = params.indexWhere(_.name == ref.refName)
                if (ind != -1) params.remove(ind)
                else tail
              }
              case _ => tail
            }
          }
          case _ => tail
        }
        i = i + 1
      }
      for (param <- params) {
        processor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst).
                put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
      }
    }
  }

  private def processCallReference(args: ScArgumentExprList, callReference: ScReferenceElement,
          ref: ResolvableReferenceExpression, assign: PsiElement, processor: BaseProcessor) = {
    for (variant <- callReference.multiResolve(false)) {
      variant match {
        case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) => {
          if (!processor.isInstanceOf[CompletionProcessor])
            fun.getParamByName(ref.refName, args.invocationCount - 1) match {
              case Some(param) => processor.execute(param, ResolveState.initial.put(ScSubstitutor.key, subst).
                      put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE))
              case None =>
            }
          else {
            //for completion only!
            funCollectNamedCompletions(fun.paramClauses, args, assign, processor, subst)
          }
        }
        case ScalaResolveResult(fun: FakePsiMethod, subst: ScSubstitutor) => {
          if (!processor.isInstanceOf[CompletionProcessor]) {
            val clausePosition = args.invocationCount - 1
            val paramByName = clausePosition match {
              case 1 => fun.params.find(p => p.name == ref.refName)
              case _ => None
            }
          } else {
            //todo:
          }
        }
        case _ =>
      }
    }
  }

  private def processTypes(e: ScExpression, processor: BaseProcessor) {
    ProgressManager.checkCanceled
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

    processor.processType(aType, e, ResolveState.initial.put(BaseProcessor.FROM_TYPE_KEY, aType))

    val candidates = processor.candidates

    aType match {
      case d: ScDesignatorType if d.isStatic => return
      case _ =>
    }
    
    if (candidates.length == 0 || (!shape && candidates.forall(!_.isApplicable)) ||
            (processor.isInstanceOf[CompletionProcessor] &&
            processor.asInstanceOf[CompletionProcessor].collectImplicits)) {
      collectImplicits(e, processor)
    }
  }

  private def collectImplicits(e: ScExpression, processor: BaseProcessor) {
    processor match {
      case _: CompletionProcessor => {
        for ((t, fun, importsUsed) <- e.implicitMap()) {
          ProgressManager.checkCanceled
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
      case _ =>
    }
    val (t: ScType, fun: PsiNamedElement, importsUsed: collection.Set[ImportUsed]) =
      ScalaPsiUtil.findImplicitConversion(e, refName, processor.kinds, this) match {
        case Some(a) => a
        case None => return
      }
    ProgressManager.checkCanceled
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