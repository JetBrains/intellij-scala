package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiMethodExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.dependency.Dependency.DependencyProcessor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createParameterFromText
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScForImpl
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitResolveResult, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
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
        //TODO should right expression really be parsed as Tuple (not as argument list)?
        infixExpr.right match {
          case t: ScTuple => t.exprs
          case op => Seq(op)
        }
      case methodCall: ScMethodCall => methodCall.argumentExpressions
    }
  }

  @tailrec
  private def getContextInfo(ref: ScReferenceExpression, e: ScExpression): ContextInfo = {
    e.getContext match {
      case generic : ScGenericCall => getContextInfo(ref, generic)
      case call: ScMethodCall if !call.isUpdateCall =>
        ContextInfo(Some(call.argumentExpressions), () => call.expectedType(), isUnderscore = false)
      case call: ScMethodCall =>
        val args = call.argumentExpressions ++ call.getContext.asInstanceOf[ScAssignment].rightExpression.toList
        ContextInfo(Some(args), () => None, isUnderscore = false)
      case section: ScUnderscoreSection => ContextInfo(None, () => section.expectedType(), isUnderscore = true)
      case infix @ ScInfixExpr.withAssoc(_, `ref`, argument) =>
        ContextInfo(argument match {
          case tuple: ScTuple => Some(tuple.exprs) // See SCL-2001
          case _: ScUnitExpr => Some(Nil) // See SCL-3485
          case e: ScParenthesisedExpr => e.innerElement match {
            case Some(expr) => Some(Seq(expr))
            case _ => Some(Nil)
          }
          case rOp => Some(Seq(rOp))
        }, () => infix.expectedType(), isUnderscore = false)
      case parents: ScParenthesisedExpr => getContextInfo(ref, parents)
      case postf: ScPostfixExpr if ref == postf.operation => getContextInfo(ref, postf)
      case pref: ScPrefixExpr if ref == pref.operation => getContextInfo(ref, pref)
      case _ => ContextInfo(None, () => e.expectedType(), isUnderscore = false)
    }
  }

  @tailrec
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

  @tailrec
  private def getTypeArgs(e: ScExpression): Seq[ScTypeElement] =
    e.getContext match {
      case generic: ScGenericCall       => generic.arguments
      case parents: ScParenthesisedExpr => getTypeArgs(parents)
      case _                            => Seq.empty
    }

  def resolve(reference: ScReferenceExpression, shapesOnly: Boolean, incomplete: Boolean): Array[ScalaResolveResult] = {
    val resolveWithName = this.resolveWithName(_: String, reference, shapesOnly, incomplete)
    val refName = reference.refName
    val context = reference.getContext

    val name = context match {
      case ScPrefixExpr(`reference`, _) => s"unary_$refName"
      case _ if ScForImpl.desugaredWithFilterKey.isIn(reference) =>
        // This is a call to withFilter in a desugared for comprehension
        // in scala version 2.11 and below withFilter will be rewritten into filter
        // we try first to resolve withFilter and if we do not get any results we try filter
        val withFilterResults = resolveWithName("withFilter")
        if (withFilterResults.nonEmpty)
          return withFilterResults
        "filter"
      case _ => refName
    }

    resolveWithName(name)
  }

  private def resolveWithName(name: String, reference: ScReferenceExpression, shapesOnly: Boolean, incomplete: Boolean): Array[ScalaResolveResult] = {
    val context = reference.getContext

    val info = getContextInfo(reference, reference)

    //expectedOption different for cases
    // val a: (Int) => Int = foo
    // and for case
    // val a: (Int) => Int = _.foo
    val expectedOption = () => info.expectedType()

    val prevInfoTypeParams = reference.getPrevTypeInfoParams

    def processor(smartProcessor: Boolean, name: String = name): MethodResolveProcessor =
      new MethodResolveProcessor(reference, name, info.arguments.toList,
        getTypeArgs(reference), prevInfoTypeParams, kinds(reference, reference, incomplete), expectedOption,
        info.isUnderscore, shapesOnly, enableTupling = true) {
        override def candidatesS: Set[ScalaResolveResult] = {
          if (!smartProcessor) super.candidatesS
          else {
            val iterator = reference.shapeResolve.iterator
            while (iterator.hasNext) {
              levelSet.add(iterator.next())
            }
            super.candidatesS
          }
        }
      }

    def smartResolve(): Array[ScalaResolveResult] = processor(smartProcessor = true).candidates

    def fallbackResolve(found: Array[ScalaResolveResult]): Array[ScalaResolveResult] = {
      // it has another resolve only in one case:
      // clazz.ref(expr)
      // clazz has method ref with one argument, but it's not ok
      // so shape resolve return this wrong result
      // however there is implicit conversion with right argument
      // this is ugly, but it can improve performance

      val applyName = "apply"

      def isApplySugarResult(r: ScalaResolveResult): Boolean = r.name == applyName && r.parentElement.nonEmpty

      val isApplySugarCall = reference.refName != applyName && found.exists(isApplySugarResult)

      if (isApplySugarCall) {
        val applyRef = createRef(reference, _ + s".$applyName")
        doResolve(applyRef, processor(smartProcessor = false, applyName))
      } else {
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
        .map(_.copy(isAssignment = true))
    }

    val result =
      if (shapesOnly) {
        doResolve(reference, processor(smartProcessor = false))
      } else {
        val smartResult = smartResolve()

        if (smartResult.exists(_.isApplicable())) smartResult
        else fallbackResolve(smartResult)
      }

    val resolveAssignment: Boolean =
      result.isEmpty &&
        (context.isInstanceOf[ScInfixExpr] || context.isInstanceOf[ScMethodCall]) &&
        name.endsWith("=") &&
        !name.startsWith("=") &&
        !Seq("!=", "<=", ">=").contains(name) &&
        !name.exists(_.isLetterOrDigit)

    if (resolveAssignment) assignmentResolve()
    else result
  }

  def doResolve(
    ref:                ScReferenceExpression,
    processor:          BaseProcessor,
    accessibilityCheck: Boolean = true,
    tryThisQualifier:   Boolean = false
  ): Array[ScalaResolveResult] = {

    def resolveUnqalified(processor: BaseProcessor): BaseProcessor =
      ref.getContext match {
        case ScSugarCallExpr(operand, operation, _) if ref == operation =>
          processTypes(operand, processor)
        case _ =>
          resolveUnqualifiedExpression(processor)
          processor
      }

    def resolveUnqualifiedExpression(processor: BaseProcessor): Unit = {
      @tailrec
      def treeWalkUp(place: PsiElement, lastParent: PsiElement): Unit = {
        if (place == null) return
        if (!place.processDeclarations(processor, ScalaResolveState.empty, lastParent, ref)) return
        place match {
          case _: ScTemplateBody | _: ScExtendsBlock => //template body and inherited members are at the same level
          case _ => if (!processor.changedLevel) return
        }
        treeWalkUp(place.getContext, place)
      }

      val context = ref.getContext

      val contextElement = (context, processor) match {
        case (x: ScAssignment, _) if x.leftExpression == ref => Some(context)
        case (_, _: DependencyProcessor)                     => None
        case (_, _: CompletionProcessor)                     => Some(ref)
        case _                                               => None
      }

      contextElement.foreach(processAssignment(_, processor))
      treeWalkUp(ref, null)
    }

    def processAssignment(assign: PsiElement, processor: BaseProcessor): Unit = {
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
          case infix@ScInfixExpr.withAssoc(_, operation, `tuple`) =>
            processAnyAssignment(tuple.exprs, infix, operation, 1, assign, processor)
          case _ =>
        }
        case p: ScParenthesisedExpr => p.getContext match {
          case infix@ScInfixExpr.withAssoc(_, operation, `p`) =>
            processAnyAssignment(p.innerElement.toSeq, infix, operation, 1, assign, processor)
          case _ =>
        }
        case _ =>
      }
    }

    def processAnyAssignment(exprs: Seq[ScExpression], call: MethodInvocation, callReference: ScReferenceExpression, invocationCount: Int,
                             assign: PsiElement, processor: BaseProcessor): Unit = {
      val refName = ref.refName

      def addParamForApplyDynamicNamed(): Unit = {
        if (!processor.isInstanceOf[CompletionProcessor]) {
          processor.execute(createParameterFromText(refName + ": Any"), ScalaResolveState.withNamedParam)
        }
      }

      for (variant <- callReference.multiResolveScala(false)) {
        def processResult(r: ScalaResolveResult) = r match {
          case ScalaResolveResult(_: ScFunction, _) if isApplyDynamicNamed(r) =>
            addParamForApplyDynamicNamed()
          case ScalaResolveResult(_, _) if call.applyOrUpdateElement.exists(isApplyDynamicNamed) =>
            addParamForApplyDynamicNamed()
          case ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor) =>
            if (!processor.isInstanceOf[CompletionProcessor]) {
              getParamByName(fun, refName, invocationCount - 1) match {
                //todo: why -1?
                case Some(param) =>
                  val rename =
                    if (!equivalent(param.name, refName)) param.deprecatedName
                    else None
                  val state = ScalaResolveState
                    .withSubstitutor(subst)
                    .withNamedParam
                    .withRename(rename)

                  processor.execute(param, state)
                case None =>
              }
            } else {
              //for completion only!
              funCollectNamedCompletions(fun.paramClauses, processor, subst, exprs, invocationCount)
            }
          case ScalaResolveResult(_: FakePsiMethod, _: ScSubstitutor) => //todo: ?
          case ScalaResolveResult(method: PsiMethod, subst) =>
            assign.getContext match {
              case args: ScArgumentExprList =>
                args.getContext match {
                  case methodCall: ScMethodCall if methodCall.isNamedParametersEnabledEverywhere =>
                    val state = ScalaResolveState.withSubstitutor(subst).withNamedParam
                    method.parameters.foreach {
                      processor.execute(_, state)
                    }
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }

        processResult(variant)
        // Consider named parameters of apply method; see SCL-2407
        variant.innerResolveResult.foreach(processResult)
      }
    }

    def processConstructorReference(args: ScArgumentExprList, assign: PsiElement, baseProcessor: BaseProcessor): Unit = {
      def processConstructor(elem: PsiElement, tp: ScType, typeArgs: Seq[ScTypeElement], arguments: Seq[ScArgumentExprList],
                             secondaryConstructors: (ScClass) => Seq[ScFunction]): Unit = {
        tp.extractClassType match {
          case Some((clazz, subst)) if !clazz.isInstanceOf[ScTemplateDefinition] && clazz.isAnnotationType =>
            if (!baseProcessor.isInstanceOf[CompletionProcessor]) {
              for (method <- clazz.getMethods) {
                method match {
                  case p: PsiAnnotationMethod =>
                    if (equivalent(p.name, ref.refName)) {
                      baseProcessor.execute(p, ScalaResolveState.empty)
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

                def tail(): Unit = {
                  if (methods.nonEmpty) methods.remove(0)
                }

                while (exprs(i) != assign) {
                  exprs(i) match {
                    case assignStmt: ScAssignment =>
                      assignStmt.leftExpression match {
                        case ref: ScReferenceExpression =>
                          val ind = methods.indexWhere(p => equivalent(p.name, ref.refName))
                          if (ind != -1) methods.remove(ind)
                          else tail()
                        case _ => tail()
                      }
                    case _ => tail()
                  }
                  i = i + 1
                }
                val state = ScalaResolveState.withSubstitutor(subst).withNamedParam
                for (method <- methods) {
                  baseProcessor.execute(method, state)
                }
              }
            }
          case Some((clazz, subst)) =>
            val processor: MethodResolveProcessor = new MethodResolveProcessor(elem, "this",
              arguments.toList.map(_.exprs), typeArgs, Seq.empty /* todo: ? */ ,
              constructorResolve = true, enableTupling = true)
            val state = ScalaResolveState.withSubstitutor(subst)
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
                    getParamByName(fun, refName, arguments.indexOf(args)) match {
                      case Some(param) =>
                        val rename =
                          if (equivalent(param.name, refName)) None
                          else param.deprecatedName.map(clean)

                        val state = ScalaResolveState
                          .withSubstitutor(subst)
                          .withNamedParam
                          .withRename(rename)

                        baseProcessor.execute(param, state)
                      case None =>
                    }
                  } else {
                    //for completion only!
                    funCollectNamedCompletions(fun.paramClauses, baseProcessor, subst, args.exprs, args.invocationCount)
                  }
                case ScalaResolveResult(constructor: ScPrimaryConstructor, _) =>
                  if (!baseProcessor.isInstanceOf[CompletionProcessor])
                    getParamByName(constructor, refName, arguments.indexOf(args)) match {
                      case Some(param) =>
                        baseProcessor.execute(param, ScalaResolveState.withSubstitutor(subst).withNamedParam)
                      case None =>
                    }
                  else {
                    //for completion only!
                    funCollectNamedCompletions(constructor.parameterList, baseProcessor, subst, args.exprs, args.invocationCount)
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
        case constrInvocation: ScConstructorInvocation =>
          val tp: ScType = constrInvocation.typeElement.`type`().getOrElse(return)
          val typeArgs: Seq[ScTypeElement] = constrInvocation.typeArgList.map(_.typeArgs).getOrElse(Seq())
          val arguments = constrInvocation.arguments
          val secondaryConstructors = (clazz: ScClass) => clazz.secondaryConstructors
          processConstructor(constrInvocation, tp, typeArgs, arguments, secondaryConstructors)
        case _ =>
      }
    }

    def funCollectNamedCompletions(clauses: ScParameters, processor: BaseProcessor,
                                   subst: ScSubstitutor, exprs: Seq[ScExpression], invocationCount: Int): Unit = {
      if (clauses.clauses.length >= invocationCount) {
        val actualClause = clauses.clauses(invocationCount - 1)
        val params = actualClause.parameters
        val usedArgNames = exprs.collect {
          case ScAssignment(ref: ScReferenceExpression, _) => ref.refName
        }
        val unusedParams = params.filterNot(p => usedArgNames.exists(equivalent(p.name, _)))

        val state = ScalaResolveState.withSubstitutor(subst).withNamedParam
        for (param <- unusedParams) {
          processor.execute(param, state)
        }
      }
    }

    def processTypes(qualifier: ScExpression, processor: BaseProcessor): BaseProcessor = {
      ProgressManager.checkCanceled()

      qualifier.getNonValueType() match {
        case Right(tpt@ScTypePolymorphicType(internal, tp)) if tp.nonEmpty &&
          !internal.isInstanceOf[ScMethodType] && !internal.isInstanceOf[UndefinedType] /* optimization */ =>
          val substed = tpt.typeParameterOrLowerSubstitutor(internal)
          processType(substed, qualifier, processor)
          if (processor.candidates.nonEmpty) return processor
        case _ =>
      }

      //if it's ordinary case
      qualifier.`type`().toOption match {
        case Some(tp) => processType(tp, qualifier, processor)
        case _ => processor
      }
    }

    def processType(aType: ScType, qualifier: ScExpression, processor: BaseProcessor): BaseProcessor = {
      val shape = processor match {
        case m: MethodResolveProcessor => m.isShapeResolve
        case _ => false
      }

      val fromType = qualifier match {
        case ref: ScReferenceExpression => ref.bind() match {
          case Some(ScalaResolveResult(_: ScSelfTypeElement, _)) => aType
          case Some(r@ScalaResolveResult(b: ScTypedDefinition, _)) if b.isStable =>
            r.fromType match {
              case Some(fT) => ScProjectionType(fT, b)
              case None => ScalaType.designator(b)
            }
          case _ => aType
        }
        case _ => aType
      }


      val state = fromType match {
        case ScDesignatorType(_: PsiPackage) => ScalaResolveState.empty
        case _                               => ScalaResolveState.withFromType(fromType)
      }
      processor.processType(aType, qualifier, state)

      val candidates = processor.candidatesS

      aType match {
        case d: ScDesignatorType if d.isStatic => return processor
        case ScDesignatorType(_: PsiPackage) => return processor
        case _ =>
      }

      if (candidates.isEmpty || (!shape && candidates.forall(!_.isApplicable())) || (processor match {
        case cp: CompletionProcessor => cp.isImplicit
        case _ => false
      })) {
        processor match {
          case rp: ResolveProcessor =>
            rp.resetPrecedence() //do not clear candidate set, we want wrong resolve, if don't found anything
          case _ =>
        }
        collectImplicits(qualifier, processor, noImplicitsForArgs = candidates.nonEmpty)

        (processor, processor.candidates) match {
          case (methodProcessor: MethodResolveProcessor, Array()) if conformsToDynamic(fromType, ref.resolveScope) =>
            val dynamicProcessor = dynamicResolveProcessor(ref, qualifier, methodProcessor)
            dynamicProcessor.processType(fromType, qualifier, state)
            dynamicProcessor
          case _ => processor
        }
      } else processor
    }

    def collectImplicits(e: ScExpression, processor: BaseProcessor, noImplicitsForArgs: Boolean): Unit = {
      import ImplicitResolveResult._

      processor match {
        case _: CompletionProcessor =>
          for {
            result <- ScImplicitlyConvertible.implicitMap(e) // todo: args?
            builder = result.builder.withImports.withImplicitType
          } processor.processType(result.`type`, e, builder.state)

          return
        case m: MethodResolveProcessor => m.noImplicitsForArgs = true
        case _ =>
      }
      val name = processor match {
        case rp: ResolveProcessor => rp.name // See SCL-2934.
        case _ => ref.refName
      }

      processImplicitConversions(name, ref, processor, noImplicitsForArgs) {
        _.withImports.withImplicitType.withType
      }(e)
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
    var res = actualProcessor.candidates
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

  /**
    * Seek parameter with appropriate name in appropriate parameter clause.
    *
    * @param name          parameter name
    * @param clausePosition = -1, effective clause number, if -1 then parameter in any explicit? clause
    */
  private def getParamByName(ml: ScMethodLike, name: String, clausePosition: Int = -1): Option[ScParameter] = {
    val parameters = clausePosition match {
      case -1 => ml.parameters
      case i if i < 0 || i >= ml.effectiveParameterClauses.length => Seq.empty
      case _ => ml.effectiveParameterClauses.apply(clausePosition).effectiveParameters
    }

    parameters.find { param =>
      equivalent(param.name, name) || param.deprecatedName.exists(equivalent(_, name))
    }
  }

}
