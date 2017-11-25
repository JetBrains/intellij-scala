package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.{PsiMethodExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.dependency.Dependency.DependencyProcessor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createParameterFromText}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitResolveResult.ResolverStateBuilder
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitResolveResult, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression._
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.Set
import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

class ReferenceExpressionResolver(implicit projectContext: ProjectContext) {

  private case class ContextInfo(arguments: Option[Seq[Expression]], expectedType: () => Option[ScType], isUnderscore: Boolean)

  private def argumentsOf(ref: PsiElement): Seq[Expression] = {
    ref.getContext match {
      case infixExpr: ScInfixExpr =>
        //TODO should rOp really be parsed as Tuple (not as argument list)?
        infixExpr.rOp match {
          case t: ScTuple => t.exprs
          case op => Seq(op)
        }
      case methodCall: ScMethodCall => methodCall.argumentExpressions
    }
  }

  private def getContextInfo(ref: ScReferenceExpression, e: ScExpression): ContextInfo = {
    e.getContext match {
      case generic : ScGenericCall => getContextInfo(ref, generic)
      case call: ScMethodCall if !call.isUpdateCall =>
        ContextInfo(Some(call.argumentExpressions), () => call.expectedType(), isUnderscore = false)
      case call: ScMethodCall =>
        val args = call.argumentExpressions ++ call.getContext.asInstanceOf[ScAssignStmt].getRExpression.toList
        ContextInfo(Some(args), () => None, isUnderscore = false)
      case section: ScUnderscoreSection => ContextInfo(None, () => section.expectedType(), isUnderscore = true)
      case inf: ScInfixExpr if ref == inf.operation =>
        ContextInfo(inf.getArgExpr match {
          case tuple: ScTuple => Some(tuple.exprs) // See SCL-2001
          case _: ScUnitExpr => Some(Nil) // See SCL-3485
          case e: ScParenthesisedExpr => e.expr match {
            case Some(expr) => Some(Seq(expr))
            case _ => Some(Nil)
          }
          case rOp => Some(Seq(rOp))
        }, () => None, isUnderscore = false)
      case parents: ScParenthesisedExpr => getContextInfo(ref, parents)
      case postf: ScPostfixExpr if ref == postf.operation => getContextInfo(ref, postf)
      case pref: ScPrefixExpr if ref == pref.operation => getContextInfo(ref, pref)
      case _ => ContextInfo(None, () => e.expectedType(), isUnderscore = false)
    }
  }

  private def kinds(ref: ScReferenceExpression, e: ScExpression, incomplete: Boolean): scala.collection.Set[ResolveTargets.Value] = {
    e.getContext match {
      case gen: ScGenericCall => kinds(ref, gen, incomplete)
      case parents: ScParenthesisedExpr => kinds(ref, parents, incomplete)
      case _: ScMethodCall | _: ScUnderscoreSection => StdKinds.methodRef
      case inf: ScInfixExpr if ref == inf.operation => StdKinds.methodRef
      case postf: ScPostfixExpr if ref == postf.operation => StdKinds.methodRef
      case pref: ScPrefixExpr if ref == pref.operation => StdKinds.methodRef
      case _ => ref.getKinds(incomplete)
    }
  }

  private def getTypeArgs(e : ScExpression) : Seq[ScTypeElement] = {
    e.getContext match {
      case generic: ScGenericCall => generic.arguments
      case parents: ScParenthesisedExpr => getTypeArgs(parents)
      case _ => Seq.empty
    }
  }

  def resolve(reference: ScReferenceExpression, shapesOnly: Boolean, incomplete: Boolean): Array[ResolveResult] = {
    val name = if (reference.isUnaryOperator) "unary_" + reference.refName else reference.refName
    val info = getContextInfo(reference, reference)

    //expectedOption different for cases
    // val a: (Int) => Int = foo
    // and for case
    // val a: (Int) => Int = _.foo
    val expectedOption = () => info.expectedType.apply()

    val prevInfoTypeParams = reference.getPrevTypeInfoParams

    def processor(smartProcessor: Boolean, name: String = name): MethodResolveProcessor =
      new MethodResolveProcessor(reference, name, info.arguments.toList,
        getTypeArgs(reference), prevInfoTypeParams, kinds(reference, reference, incomplete), expectedOption,
        info.isUnderscore, shapesOnly, enableTupling = true) {
        override def candidatesS: Set[ScalaResolveResult] = {
          if (!smartProcessor) super.candidatesS
          else {
            val iterator = reference.shapeResolve.map(_.asInstanceOf[ScalaResolveResult]).iterator
            while (iterator.hasNext) {
              levelSet.add(iterator.next())
            }
            super.candidatesS
          }
        }
      }

    def smartResolve(): Array[ScalaResolveResult] = processor(smartProcessor = true).candidates

    def fallbackResolve(found: Array[ScalaResolveResult]): Array[ResolveResult] = {
      // it has another resolve only in one case:
      // clazz.ref(expr)
      // clazz has method ref with one argument, but it's not ok
      // so shape resolve return this wrong result
      // however there is implicit conversion with right argument
      // this is ugly, but it can improve performance

      val applyName = "apply"
      val isApplySugarCall = reference.refName != applyName && found.exists(_.name == applyName)

      if (isApplySugarCall) {
        val applyRef = createRef(reference, _ + s".$applyName")
        doResolve(applyRef, processor(smartProcessor = false, applyName))
      }
      else {
        doResolve(reference, processor(smartProcessor = false), tryThisQualifier = true)
      }
    }

    def assignmentResolve() = {
      val assignProcessor = new MethodResolveProcessor(
        reference,
        reference.refName.init,
        List(argumentsOf(reference)),
        Nil,
        prevInfoTypeParams,
        isShapeResolve = shapesOnly,
        enableTupling = true)

      doResolve(reference, assignProcessor)
        .collect {
          case r: ScalaResolveResult => r.copy(isAssignment = true): ResolveResult
        }
    }

    val result =
      if (shapesOnly) {
        doResolve(reference, processor(smartProcessor = false))
      } else {
        val smartResult = smartResolve()

        if (smartResult.exists(_.isApplicable())) smartResult
        else fallbackResolve(smartResult)
      }

    if (result.isEmpty && reference.isAssignmentOperator) {
      assignmentResolve()
    } else {
      result.toArray
    }
  }

  def doResolve(ref: ScReferenceExpression, processor: BaseProcessor, accessibilityCheck: Boolean = true,
                tryThisQualifier: Boolean = false): Array[ResolveResult] = {
    def resolveUnqalified(processor: BaseProcessor): BaseProcessor = {
      ref.getContext match {
        case ScSugarCallExpr(operand, operation, _) if ref == operation =>
          processTypes(operand, processor)
        case _ =>
          resolveUnqualifiedExpression(processor)
          processor
      }
    }

    def resolveUnqualifiedExpression(processor: BaseProcessor) {
      @tailrec
      def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
        if (place == null) return
        if (!place.processDeclarations(processor, ResolveState.initial(), lastParent, ref)) return
        place match {
          case (_: ScTemplateBody | _: ScExtendsBlock) => //template body and inherited members are at the same level
          case _ => if (!processor.changedLevel) return
        }
        treeWalkUp(place.getContext, place)
      }

      val context = ref.getContext
      val contextElement = (context, processor) match {
        case (x: ScAssignStmt, _) if x.getLExpression == ref => Some(context)
        case (_, _: DependencyProcessor) => None
        case (_, _: CompletionProcessor) => Some(ref)
        case _ => None
      }

      contextElement.foreach(processAssignment(_, processor))

      treeWalkUp(ref, null)
    }

    def processAssignment(assign: PsiElement, processor: BaseProcessor) {
      assign.getContext match {
        //trying to resolve naming parameter
        case args: ScArgumentExprList =>
          args.callReference match {
            case Some(callReference) if args.getContext.isInstanceOf[MethodInvocation] =>
              processAnyAssignment(args.exprs, args.getContext.asInstanceOf[MethodInvocation], callReference,
                args.invocationCount, assign, processor)
            case None => processConstructorReference(args, assign, processor)
          }
        case tuple: ScTuple => tuple.getContext match {
          case inf: ScInfixExpr if inf.getArgExpr == tuple =>
            processAnyAssignment(tuple.exprs, inf, inf.operation, 1, assign, processor)
          case _ =>
        }
        case p: ScParenthesisedExpr => p.getContext match {
          case inf: ScInfixExpr if inf.getArgExpr == p =>
            processAnyAssignment(p.expr.toSeq, inf, inf.operation, 1, assign, processor)
          case _ =>
        }
        case _ =>
      }
    }

    def processAnyAssignment(exprs: Seq[ScExpression], call: MethodInvocation, callReference: ScReferenceExpression, invocationCount: Int,
                             assign: PsiElement, processor: BaseProcessor) {
      val refName = ref.refName
      for (variant <- callReference.multiResolve(false)) {
        def processResult(r: ScalaResolveResult) = r match {
          case ScalaResolveResult(fun: ScFunction, _) if r.isDynamic &&
            fun.name == APPLY_DYNAMIC_NAMED =>
            //add synthetic parameter
            if (!processor.isInstanceOf[CompletionProcessor]) {
              val state: ResolveState = ResolveState.initial().put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE)
              processor.execute(createParameterFromText(refName + ": Any"), state)
            }
          case ScalaResolveResult(_, _) if call.applyOrUpdateElement.exists(_.isDynamic) &&
            call.applyOrUpdateElement.get.name == APPLY_DYNAMIC_NAMED =>
            //add synthetic parameter
            if (!processor.isInstanceOf[CompletionProcessor]) {
              val state: ResolveState = ResolveState.initial().put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE)
              processor.execute(createParameterFromText(refName + ": Any"), state)
            }
          case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) =>
            if (!processor.isInstanceOf[CompletionProcessor]) {
              fun.getParamByName(refName, invocationCount - 1) match {
                //todo: why -1?
                case Some(param) =>
                  var state = ResolveState.initial.put(ScSubstitutor.key, subst).
                    put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE)
                  if (!ScalaNamesUtil.equivalent(param.name, refName)) {
                    state = state.put(ResolverEnv.nameKey, ScalaNamesUtil.clean(param.deprecatedName.get))
                  }
                  processor.execute(param, state)
                case None =>
              }
            } else {
              //for completion only!
              funCollectNamedCompletions(fun.paramClauses, assign, processor, subst, exprs, invocationCount)
            }
          case ScalaResolveResult(_: FakePsiMethod, _: ScSubstitutor) => //todo: ?
          case ScalaResolveResult(method: PsiMethod, subst) =>
            assign.getContext match {
              case args: ScArgumentExprList =>
                args.getContext match {
                  case methodCall: ScMethodCall if methodCall.isNamedParametersEnabledEverywhere =>
                    method.parameters.foreach {
                      p =>
                        processor.execute(p, ResolveState.initial().put(ScSubstitutor.key, subst).
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

    def processConstructorReference(args: ScArgumentExprList, assign: PsiElement, baseProcessor: BaseProcessor) {
      def processConstructor(elem: PsiElement, tp: ScType, typeArgs: Seq[ScTypeElement], arguments: Seq[ScArgumentExprList],
                             secondaryConstructors: (ScClass) => Seq[ScFunction]) {
        tp.extractClassType match {
          case Some((clazz, subst)) if !clazz.isInstanceOf[ScTemplateDefinition] && clazz.isAnnotationType =>
            if (!baseProcessor.isInstanceOf[CompletionProcessor]) {
              for (method <- clazz.getMethods) {
                method match {
                  case p: PsiAnnotationMethod =>
                    if (ScalaNamesUtil.equivalent(p.name, ref.refName)) {
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
                          val ind = methods.indexWhere(p => ScalaNamesUtil.equivalent(p.name, ref.refName))
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
            val refName = ref.refName
            for (candidate <- processor.candidatesS) {
              candidate match {
                case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) =>
                  if (!baseProcessor.isInstanceOf[CompletionProcessor]) {
                    fun.getParamByName(refName, arguments.indexOf(args)) match {
                      case Some(param) =>
                        var state = ResolveState.initial.put(ScSubstitutor.key, subst).
                          put(CachesUtil.NAMED_PARAM_KEY, java.lang.Boolean.TRUE)
                        if (!ScalaNamesUtil.equivalent(param.name, refName)) {
                          state = state.put(ResolverEnv.nameKey, ScalaNamesUtil.clean(param.deprecatedName.get))
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
                    constructor.getParamByName(refName, arguments.indexOf(args)) match {
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
          val tp: ScType = clazz.asInstanceOf[ScClass].`type`().getOrElse(return)
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
          val tp: ScType = constr.typeElement.`type`().getOrElse(return)
          val typeArgs: Seq[ScTypeElement] = constr.typeArgList.map(_.typeArgs).getOrElse(Seq())
          val arguments = constr.arguments
          val secondaryConstructors = (clazz: ScClass) => clazz.secondaryConstructors
          processConstructor(constr, tp, typeArgs, arguments, secondaryConstructors)
        case _ =>
      }
    }

    def funCollectNamedCompletions(clauses: ScParameters, assign: PsiElement, processor: BaseProcessor,
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
                  val ind = params.indexWhere(p => ScalaNamesUtil.equivalent(p.name, ref.refName))
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

    def processTypes(e: ScExpression, processor: BaseProcessor): BaseProcessor = {
      ProgressManager.checkCanceled()

      e.getNonValueType() match {
        case Right(tpt@ScTypePolymorphicType(internal, tp)) if tp.nonEmpty &&
          !internal.isInstanceOf[ScMethodType] && !internal.isInstanceOf[UndefinedType] /* optimization */ =>
          val substed = tpt.abstractTypeSubstitutor.subst(internal)
          processType(substed, e, processor)
          if (processor.candidates.nonEmpty) return processor
        case _ =>
      }

      //if it's ordinary case
      e.`type`().toOption match {
        case Some(tp) => processType(tp, e, processor)
        case _ => processor
      }
    }

    def processType(aType: ScType, e: ScExpression, processor: BaseProcessor): BaseProcessor = {
      val shape = processor match {
        case m: MethodResolveProcessor => m.isShapeResolve
        case _ => false
      }

      val fromType = e match {
        case ref: ScReferenceExpression => ref.bind() match {
          case Some(ScalaResolveResult(_: ScSelfTypeElement, _)) => aType
          case Some(r@ScalaResolveResult(b: ScTypedDefinition, _)) if b.isStable =>
            r.fromType match {
              case Some(fT) => ScProjectionType(fT, b, superReference = false)
              case None => ScalaType.designator(b)
            }
          case _ => aType
        }
        case _ => aType
      }


      var state = ResolveState.initial()
      fromType match {
        case ScDesignatorType(_: PsiPackage) =>
        case _ =>
          state = state.put(BaseProcessor.FROM_TYPE_KEY, fromType)
      }
      processor.processType(aType, e, state)

      val candidates = processor.candidatesS

      aType match {
        case d: ScDesignatorType if d.isStatic => return processor
        case ScDesignatorType(_: PsiPackage) => return processor
        case _ =>
      }

      if (candidates.isEmpty || (!shape && candidates.forall(!_.isApplicable())) ||
        processor.isInstanceOf[ImplicitCompletionProcessor]) {
        processor match {
          case rp: ResolveProcessor =>
            rp.resetPrecedence() //do not clear candidate set, we want wrong resolve, if don't found anything
          case _ =>
        }
        collectImplicits(e, processor, noImplicitsForArgs = candidates.nonEmpty)

        (processor, processor.candidates) match {
          case (methodProcessor: MethodResolveProcessor, Array()) => processDynamic(fromType, e, methodProcessor)
          case _ => processor
        }
      } else processor
    }

    def processDynamic(`type`: ScType, expression: ScExpression, processor: MethodResolveProcessor): BaseProcessor = {
      val maybeDynamicType = ref.elementScope.getCachedClass("scala.Dynamic")
        .map(ScDesignatorType(_))

      if (!maybeDynamicType.exists(`type`.conforms)) return processor

      val expressionsOrContext = ref.getContext match {
        case postfix: ScPostfixExpr => Left(postfix)
        case MethodInvocation(`ref`, expressions) => Right(expressions)
        case _ => Left(ref)
      }

      val name = expressionsOrContext match {
        case Right(expressions) => getDynamicNameForMethodInvocation(expressions)
        case Left(reference) =>
          reference.getContext match {
            case ScAssignStmt(`reference`, _) => UPDATE_DYNAMIC
            case _ => SELECT_DYNAMIC
          }
      }

      val emptyStringExpression = createExpressionFromText("\"\"")(expression.projectContext)

      val newProcessor = new MethodResolveProcessor(expression, name, List(List(emptyStringExpression), expressionsOrContext.getOrElse(Seq.empty)),
        processor.typeArgElements, processor.prevTypeInfo, processor.kinds, processor.expectedOption,
        processor.isUnderscore, processor.isShapeResolve, processor.constructorResolve, processor.noImplicitsForArgs,
        processor.enableTupling, processor.selfConstructorResolve, isDynamic = true)

      newProcessor.processType(`type`, expression, ResolveState.initial.put(BaseProcessor.FROM_TYPE_KEY, `type`))
      newProcessor
    }

    def collectImplicits(e: ScExpression, processor: BaseProcessor, noImplicitsForArgs: Boolean) {
      def builder(result: ImplicitResolveResult): ResolverStateBuilder = {
        ProgressManager.checkCanceled()
        new ImplicitResolveResult.ResolverStateBuilder(result).withImports
          .withImplicitType
          .withImplicitFunction
      }

      processor match {
        case _: CompletionProcessor =>
          new ScImplicitlyConvertible(e).implicitMap().foreach { result =>
            //todo: args?
            val state = builder(result).state
            processor.processType(result.`type`, e, state)
          }
          return
        case m: MethodResolveProcessor => m.noImplicitsForArgs = true
        case _ =>
      }
      val name = processor match {
        case rp: ResolveProcessor => rp.name // See SCL-2934.
        case _ => ref.refName
      }

      ScalaPsiUtil.findImplicitConversion(e, name, ref, processor, noImplicitsForArgs).foreach { result =>
        val state = builder(result).withType.state
        processor.processType(result.typeWithDependentSubstitutor, e, state)
      }
    }

    if (!accessibilityCheck) processor.doNotCheckAccessibility()

    val actualProcessor = ref.qualifier match {
      case None =>
        resolveUnqalified(processor)
      case Some(superQ: ScSuperReference) =>
        ResolveUtils.processSuperReference(superQ, processor, ref)
        processor
      case Some(q) =>
        processTypes(q, processor)
    }
    var res = actualProcessor.rrcandidates
    if (accessibilityCheck && res.length == 0) {
      res = doResolve(ref, processor, accessibilityCheck = false)
    }
    if (res.nonEmpty && res.forall(!_.isValidResult) && ref.qualifier.isEmpty && tryThisQualifier) {
      val thisExpr = createRef(ref, "this." + _)
      res = doResolve(thisExpr, processor, accessibilityCheck)
    }
    res
  }

  private def createRef(ref: ScReferenceExpression, textUpdate: String => String): ScReferenceExpression = {
    val newText = textUpdate(ref.getText)
    ScalaPsiElementFactory.createExpressionFromText(newText, ref.getContext)
      .asInstanceOf[ScReferenceExpression]
  }
}