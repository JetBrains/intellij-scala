package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dependency.Dependency.DependencyProcessor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, ScConstructorInvocation, ScMethodLike}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createParameterFromText
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScForImpl
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.{ExtensionMethod, ScExpressionForExpectedTypesEx}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.language.implicitConversions

class ReferenceExpressionResolver(implicit projectContext: ProjectContext) {

  private case class ContextInfo(arguments: Option[Seq[Expression]], expectedType: () => Option[ScType], isUnderscore: Boolean)

  private def argumentsOf(ref: PsiElement): Seq[Expression] = {
    ref.getContext match {
      case infixExpr: ScInfixExpr =>
        //TODO should right expression really be parsed as Tuple (not as argument list)?
        infixExpr.right match {
          case t: ScTuple => t.exprs
          case op         => Seq(op)
        }
      case methodCall: ScMethodCall => methodCall.argumentExpressions
    }
  }

  @tailrec
  private def getContextInfo(ref: ScReferenceExpression, e: ScExpression): ContextInfo = {
    e.getContext match {
      case generic: ScGenericCall => getContextInfo(ref, generic)
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
  private def kindsForRef(
    ref:        ScReferenceExpression,
    e:          ScExpression,
    incomplete: Boolean
  ): Set[ResolveTargets.Value] = e.getContext match {
    case gen: ScGenericCall                             => kindsForRef(ref, gen, incomplete)
    case parents: ScParenthesisedExpr                   => kindsForRef(ref, parents, incomplete)
    case _: ScMethodCall | _: ScUnderscoreSection       => StdKinds.methodRef
    case inf: ScInfixExpr if ref == inf.operation       => StdKinds.methodRef
    case postf: ScPostfixExpr if ref == postf.operation => StdKinds.methodRef
    case pref: ScPrefixExpr if ref == pref.operation    => StdKinds.methodRef
    case _                                              => ref.getKinds(incomplete)
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

    def processor(
      smartProcessor: Boolean,
      name:           String                    = name,
      kinds:          Set[ResolveTargets.Value] = kindsForRef(reference, reference, incomplete)
    ): MethodResolveProcessor =
      new MethodResolveProcessor(
        reference,
        name,
        info.arguments.toList,
        getTypeArgs(reference),
        prevInfoTypeParams,
        kinds,
        expectedOption,
        info.isUnderscore,
        isShapeResolve = shapesOnly,
        enableTupling  = true
      ) {
        override def candidatesS: Set[ScalaResolveResult] =
          if (!smartProcessor) super.candidatesS
          else {
            val iterator = reference.shapeResolve.iterator
            while (iterator.hasNext) {
              levelSet.add(iterator.next())
            }
            super.candidatesS
          }
      }

    def smartResolve(): Array[ScalaResolveResult] = processor(smartProcessor = true).candidates

    def resolveConstructorProxies(srrs: Array[ScalaResolveResult]): Array[ScalaResolveResult] = {
      def tryResolveSpecificProxies: Array[ScalaResolveResult] =
        if (srrs.length != 1) srrs
        else srrs.head.element match {
          case obj: ScObject if obj.allFunctionsByName(CommonNames.Apply).isEmpty =>
            val cls  = obj.baseCompanion
            val proc = processor(smartProcessor = false, kinds = Set(ResolveTargets.CLASS))
            cls.foreach(proc.execute(_, ScalaResolveState.withImportsUsed(srrs.head.importsUsed)))
            proc.candidates
          case _ => srrs
        }

      def inMethodCallContext: Boolean =
        context match {
          case _: ScMethodCall | _: ScGenericCall => true
          case _                                  => false
        }

      if (!inMethodCallContext)             srrs
      else if (!reference.isInScala3Module) srrs
      else if (srrs.isEmpty)
        reference.getContext match {
          case _: MethodInvocation | _: ScGenericCall =>
            val isExplicitApplyReference = name == CommonNames.Apply

            val amendedRef = reference match {
              case ScReferenceExpression.withQualifier(qual: ScReferenceExpression)
                if isExplicitApplyReference => qual
              case _ => reference
            }

            val proc    = processor(smartProcessor = false, name = amendedRef.refName, kinds = Set(ResolveTargets.CLASS))
            val proxies = doResolve(amendedRef, proc)

            if (proxies.nonEmpty && (amendedRef ne reference)) {
              /**
               * This is the case, where constructor proxy is accessed with an explicit .apply call,
               * to avoid having an unresolved reference to synthetic object, mark it with the
               * [[ConstructorProxieHolderKey]] key.
               */
              import ReferenceExpressionResolver.ConstructorProxyHolderKey
              reference.qualifier.foreach(_.putUserData(ConstructorProxyHolderKey, true))
            }

            proxies
          case _ => srrs
        }
      else tryResolveSpecificProxies
    }

    def fallbackResolve(found: Array[ScalaResolveResult]): Array[ScalaResolveResult] = {
      // it has another resolve only in one case:
      // clazz.ref(expr)
      // clazz has method ref with one argument, but it's not ok
      // so shape resolve return this wrong result
      // however there is implicit conversion with right argument
      // this is ugly, but it can improve performance

      def isApplySugarResult(r: ScalaResolveResult): Boolean =
        r.name == CommonNames.Apply && r.parentElement.nonEmpty

      val isApplySugarCall = reference.refName != CommonNames.Apply && found.exists(isApplySugarResult)

      if (isApplySugarCall) {
        val applyRef = createRef(reference, s"${reference.getText}.${CommonNames.Apply}")
        doResolve(applyRef, processor(smartProcessor = false, CommonNames.Apply))
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
        val shapeResolve = doResolve(reference, processor(smartProcessor = false))
        resolveConstructorProxies(shapeResolve)
      } else {
        val smartResult = smartResolve()
        val withProxies = resolveConstructorProxies(smartResult)

        if (withProxies.exists(_.isApplicable())) withProxies
        else                                      fallbackResolve(withProxies)
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

    def resolveUnqualified(processor: BaseProcessor): BaseProcessor =
      ref.getContext match {
        case ScSugarCallExpr(operand, operation, _) if ref == operation =>
          processQualifier(operand, processor)
        case (gc: ScGenericCall) childOf ScInfixExpr(lhs, ref, _) if ref == gc.referencedExpr =>
          processQualifier(lhs, processor)
        case _ =>
          resolveUnqualifiedExpression(processor)
          processor
      }

    def resolveUnqualifiedExpression(processor: BaseProcessor): Unit = {
      @tailrec
      def treeWalkUp(place: PsiElement, lastParent: PsiElement, state: ResolveState): Unit = {
        if (place == null)
          return
        if (!place.processDeclarations(processor, state, lastParent, ref))
          return
        place match {
          case _: ScTemplateBody | _: ScExtendsBlock => //template body and inherited members are at the same level
          case _ =>
            if (!processor.changedLevel)
              return
        }

        val newState = place match {
          /** An extension method `f` can be referenced by a simple identifier (and rewritten by the compiler to the qualified form)
           * if it is called from inside the body of an extension method `g`, which is defined in the same collective extension.
           * To support resolve of such cases we store information about enclosing extension in the resolve state.
           */
          case fdef @ ExtensionMethod() => fdef.extensionMethodOwner.fold(state)(state.withExtensionContext)
          case _                        => state
        }

        treeWalkUp(place.getContext, place, newState)
      }

      val context = ref.getContext

      val contextElement = (context, processor) match {
        case (x: ScAssignment, _) if x.leftExpression == ref => Some(context)
        case (_, _: DependencyProcessor)                     => None
        case (_, _: CompletionProcessor)                     => Some(ref)
        case _                                               => None
      }

      contextElement.foreach(processAssignment(_, processor))
      treeWalkUp(ref, null, ScalaResolveState.empty)
    }

    def processAssignment(assign: PsiElement, processor: BaseProcessor): Unit = assign.getContext match {
      //trying to resolve naming parameter
      case args: ScArgumentExprList =>
        args.getContext match {
          case invocation: MethodInvocation =>
            processMethodAssignment(args, invocation, processor)
          case invocation: ConstructorInvocationLike =>
            processConstructorReference(args, invocation, assign, processor, args.invocationCount - 1)
          case _ =>
        }
      case tuple: ScTuple => tuple.getContext match {
        case infix@ScInfixExpr.withAssoc(_, operation, `tuple`) =>
          processAnyAssignment(tuple.exprs, infix, operation, processor)
        case _ =>
      }
      case p: ScParenthesisedExpr => p.getContext match {
        case infix@ScInfixExpr.withAssoc(_, operation, `p`) =>
          processAnyAssignment(p.innerElement.toSeq, infix, operation, processor)
        case _ =>
      }
      case _ =>
    }

    def processMethodAssignment(args: ScArgumentExprList,
                                call: MethodInvocation,
                                processor: BaseProcessor): Unit =
      args.callReference.foreach { reference =>
        val isNamedParametersEnabled = call match {
          case call: ScMethodCall => call.isNamedParametersEnabledEverywhere
          case _                  => false
        }

        processAnyAssignment(
          args.exprs,
          call,
          reference,
          processor,
          args.invocationCount - 1,
          isNamedParametersEnabled
        )
      }

    def processAnyAssignment(exprs: Seq[ScExpression],
                             call: MethodInvocation,
                             callReference: ScReferenceExpression,
                             processor: BaseProcessor,
                             index: Int = 0,
                             isNamedParametersEnabled: Boolean = false): Unit = {
      val refName = ref.refName

      def addParamForApplyDynamicNamed(): Unit = processor match {
        case _: CompletionProcessor =>
        case _ =>
          processor.execute(
            createParameterFromText(refName + ": Any"),
            ScalaResolveState.withNamedParam
          )
      }

      def processResult(result: ScalaResolveResult, index: Int): Unit = result.element match {
        case _: ScFunction if isApplyDynamicNamed(result) =>
          addParamForApplyDynamicNamed()
        case _ if call.applyOrUpdateElement.exists(isApplyDynamicNamed) =>
          addParamForApplyDynamicNamed()
        case fun: ScFunction =>
          val substitutor = result.substitutor
          processor match {
            case completionProcessor: CompletionProcessor =>
              collectNamedCompletions(fun.paramClauses, completionProcessor, substitutor, exprs, index)
            case _ =>
              getParamByName(fun, refName, index) match {
                //todo: why -1?
                case Some(param) =>
                  val rename =
                    if (!equivalent(param.name, refName)) param.deprecatedName
                    else                                  None

                  val state = ScalaResolveState
                    .withSubstitutor(substitutor)
                    .withNamedParam
                    .withRename(rename)

                  processor.execute(param, state)
                case None =>
              }
          }
        case _: FakePsiMethod => //todo: ?
        case method: PsiMethod if isNamedParametersEnabled =>
          val state = ScalaResolveState
            .withSubstitutor(result.substitutor)
            .withNamedParam

          method.parameters.foreach {
            processor.execute(_, state)
          }
        case _ =>
      }

      def tryProcessApplyMethodArgs(): Unit = {
        @tailrec
        def traverseInvokedExprs(call: ScExpression, dropped: Int): Unit = call match {
          case mc: MethodInvocation =>
            val tp            = mc.`type`().getOrAny
            val applyResolves = mc.shapeResolveApplyMethod(tp, mc.argumentExpressions, mc.toOption)

            applyResolves.foreach(processResult(_, dropped))
            if (processor.candidates.isEmpty) traverseInvokedExprs(mc.getEffectiveInvokedExpr, dropped + 1)
          case _ => ()
        }
        traverseInvokedExprs(call.getEffectiveInvokedExpr, 0)
      }

      for (variant <- callReference.multiResolveScala(false)) {
        processResult(variant, index)
        // Consider named parameters of apply method; see SCL-2407
        variant.innerResolveResult.foreach(processResult(_, index))
        // Check if argument clause is actuall an apply method invocation SCL-17892
        if (processor.candidates.isEmpty) tryProcessApplyMethodArgs()
      }
    }

    def processConstructorReference(args: ScArgumentExprList,
                                    invocation: ConstructorInvocationLike,
                                    assign: PsiElement,
                                    baseProcessor: BaseProcessor,
                                    index: Int): Unit = {
      def processConstructor(typeable: Typeable)
                            (isTargetClass: ScConstructorOwner => Boolean)
                            (isAcceptableConstructor: ScFunction => Boolean): Unit = for {
        scType <- typeable.`type`().toOption
        (clazz, subst) <- scType.extractClassType
      } {
        if (!clazz.isInstanceOf[ScTemplateDefinition] && clazz.isAnnotationType) {
          baseProcessor match {
            case completionProcessor: CompletionProcessor =>
              if (index == 0) {
                val methods = clazz.getMethods.collect {
                  case annotationMethod: PsiAnnotationMethod => annotationMethod
                }.toBuffer

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
                  completionProcessor.execute(method, state)
                }
              }
            case _ =>
              for {
                method <- clazz.getMethods.toSeq.filterByType[PsiAnnotationMethod]
                if equivalent(method.name, ref.refName)
              } baseProcessor.execute(method, ScalaResolveState.empty)
          }
        } else {
          val arguments = invocation.arguments.toList

          val processor = new MethodResolveProcessor(
            invocation,
            "this",
            arguments.map(_.exprs),
            invocation.typeArgList.fold(Seq.empty[ScTypeElement])(_.typeArgs),
            Seq.empty /* todo: ? */ ,
            constructorResolve = true,
            enableTupling = true
          )

          val state = ScalaResolveState.withSubstitutor(subst)
          clazz match {
            case clazz: ScConstructorOwner =>
              if (isTargetClass(clazz)) {
                for {
                  constructor <- clazz.secondaryConstructors
                  if isAcceptableConstructor(constructor)
                } processor.execute(constructor, state)
              }

              for {
                constructor <- clazz.constructor
              } processor.execute(constructor, state)
            case _ =>
              for (constr <- clazz.getConstructors) {
                processor.execute(constr, state)
              }
          }

          val refName = ref.refName
          for (candidate <- processor.candidatesS) {
            candidate.element match {
              case method: ScMethodLike =>
                val isFunction = method.isInstanceOf[ScFunction]
                baseProcessor match {
                  case baseProcessor: CompletionProcessor =>
                    collectNamedCompletions(
                      method.parameterList,
                      baseProcessor,
                      if (isFunction) candidate.substitutor else subst,
                      args.exprs,
                      index
                    )
                  case _ =>
                    for {
                      parameter <- getParamByName(method, refName, arguments.indexOf(args))

                      name = if (isFunction && !equivalent(parameter.name, refName))
                        parameter.deprecatedName.map(clean)
                      else
                        None
                    } baseProcessor.execute(
                      parameter,
                      ScalaResolveState
                        .withSubstitutor(subst)
                        .withNamedParam
                        .withRename(name)
                    )
                }
              case _ =>
            }
          }
        }
      }

      invocation match {
        case invocation: ScSelfInvocation =>
          getContextOfType(invocation, true, classOf[ScClass]) match {
            case null =>
            case clazz =>
              processConstructor(clazz)(_ == clazz) { constructor =>
                constructor.getTextRange.getStartOffset < invocation.getTextRange.getStartOffset &&
                  !isContextAncestor(constructor, invocation, true)
              }
          }
        case invocation: ScConstructorInvocation =>
          processConstructor(invocation.typeElement)(Function.const(true))(Function.const(true))
      }
    }

    def processQualifier(qualifier: ScExpression, processor: BaseProcessor): BaseProcessor = {
      ProgressManager.checkCanceled()

      qualifier.getNonValueType() match {
        case Right(tpt @ ScTypePolymorphicType(internal, tp)) if tp.nonEmpty &&
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
        case _                         => false
      }

      val fromType = qualifier match {
        case ref: ScReferenceExpression =>
          ref.bind() match {
            case Some(ScalaResolveResult(_: ScSelfTypeElement, _)) => aType
            case Some(r @ ScalaResolveResult(b: ScTypedDefinition, _)) if b.isStable =>
              r.fromType match {
                case Some(fT) => ScProjectionType(fT, b)
                case None     => ScalaType.designator(b)
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
        case ScDesignatorType(_: PsiPackage)   => return processor
        case _                                 =>
      }

      val withImplicitConversion = processor match {
        case cp: CompletionProcessor => cp.withImplicitConversions
        case _                       => false
      }

      val noApplicableCandidates =
        candidates.isEmpty ||
          (!shape && candidates.forall(!_.isApplicable()))

      if (noApplicableCandidates || withImplicitConversion) {
        ImplicitConversionResolveResult.processImplicitConversionsAndExtensions(
          targetNameFor(processor),
          ref,
          processor,
          noImplicitsForArgs = candidates.nonEmpty,
          forCompletion = processor.is[CompletionProcessor]
        )(_.withImports.withImplicitType.withType)(qualifier)


        (processor, processor.candidates) match {
          case (methodProcessor: MethodResolveProcessor, Array()) if conformsToDynamic(fromType, ref.resolveScope) =>
            val dynamicProcessor = dynamicResolveProcessor(ref, qualifier, methodProcessor)
            dynamicProcessor.processType(fromType, qualifier, state)
            dynamicProcessor
          case _ => processor
        }
      } else processor
    }

    def targetNameFor(processor: BaseProcessor): Option[String] = processor match {
      case _: CompletionProcessor => None
      case processor: ResolveProcessor =>
        processor.resetPrecedence() //do not clear candidate set, we want wrong resolve, if don't found anything
        processor match {
          case processor: MethodResolveProcessor => processor.noImplicitsForArgs = true
          case _ =>
        }

        Some(processor.name) // See SCL-2934.
      case _ => Some(ref.refName)
    }

    if (!accessibilityCheck) processor.doNotCheckAccessibility()

    val actualProcessor = ref.qualifier match {
      case None => resolveUnqualified(processor)
      case Some(superQ: ScSuperReference) =>
        ResolveUtils.processSuperReference(superQ, processor, ref)
        processor
      case Some(q) => processQualifier(q, processor)
    }

    var res = actualProcessor.candidates
    if (accessibilityCheck && res.isEmpty) {
      res = doResolve(ref, processor, accessibilityCheck = false)
    }

    if (res.nonEmpty && res.forall(!_.isValidResult) && ref.qualifier.isEmpty && tryThisQualifier) {
      val thisExpr = createRef(ref, s"this.${ref.getText}")
      res = doResolve(thisExpr, processor, accessibilityCheck)
    }

    res
  }

  private def createRef(ref: ScReferenceExpression, text: String): ScReferenceExpression =
    ScalaPsiElementFactory.createExpressionFromText(text, ref.getContext)
      .asInstanceOf[ScReferenceExpression]

  /**
   * Seek parameter with appropriate name in appropriate parameter clause.
   *
   * @param name        parameter name
   * @param clauseIndex = -1, effective clause number, if -1 then parameter in any explicit? clause
   */
  private def getParamByName(ml: ScMethodLike,
                             name: String,
                             clauseIndex: Int = -1): Option[ScParameter] = {
    val parameters = clauseIndex match {
      case -1 => ml.parameters
      case _  => ml.parametersInClause(clauseIndex)
    }

    parameters.find { parameter =>
      equivalent(parameter.name, name) || parameter.deprecatedName.exists(equivalent(_, name))
    }
  }

  private def collectNamedCompletions(parameters: ScParameters,
                                      processor: CompletionProcessor,
                                      substitutor: ScSubstitutor,
                                      expressions: Seq[ScExpression],
                                      index: Int): Unit = {
    val clauses = parameters.clauses
    if (0 <= index && index < clauses.length) {
      val usedArgNames = expressions.collect {
        case ScAssignment(reference: ScReferenceExpression, _) => reference.refName
      }

      val state = ScalaResolveState
        .withSubstitutor(substitutor)
        .withNamedParam

      for {
        parameter <- clauses(index).parameters
        if !usedArgNames.exists(equivalent(parameter.name, _))
      } processor.execute(parameter, state)
    }
  }
}

object ReferenceExpressionResolver {
  import com.intellij.openapi.util.Key

  val ConstructorProxyHolderKey: Key[Boolean] = Key.create[Boolean]("scala.resolve.synthetic.constructor.proxy.holder")
}