package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dependency.Dependency.DependencyProcessor
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeArgument}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, ScConstructorInvocation, ScMethodLike}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScEnum, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createParameterFromText
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{PatternTypeInference, ScForImpl}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.PsiElementForExpectedTypesEx
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor._
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor._
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.language.implicitConversions

class ReferenceExpressionResolver(implicit projectContext: ProjectContext) {
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

  def resolve(reference: ScReferenceExpression, shapesOnly: Boolean, incomplete: Boolean): Array[ScalaResolveResult] = {
    val resolveWithName = this.resolveWithName(_: String, reference, shapesOnly, incomplete)
    val refName         = reference.refName
    val context         = reference.getContext

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

  private def inMethodCallContext(reference: ScReferenceExpression): Boolean =
    reference.getContext match {
      case gen: ScGenericCall    if gen.referencedExpr == reference => true
      case inv: MethodInvocation if inv.getInvokedExpr == reference => true
      case _                                                        => false
    }

  private def resolveWithName(
    name:       String,
    reference:  ScReferenceExpression,
    shapesOnly: Boolean,
    incomplete: Boolean
  ): Array[ScalaResolveResult] = {
    val context = reference.getContext

    val info = getInvocationInfo(reference, reference)

    //expectedOption different for cases
    // val a: (Int) => Int = foo
    // and for case
    // val a: (Int) => Int = _.foo
    val expectedOption = () => info.expectedType()

    val prevInfoTypeParams = reference.getPrevTypeInfoParams

    def processor(
      name:  String                    = name,
      kinds: Set[ResolveTargets.Value] = kindsForRef(reference, reference, incomplete)
    ): MethodResolveProcessor =
      new MethodResolveProcessor(
        reference,
        name,
        info.invocationClauses,
        prevInfoTypeParams,
        kinds,
        expectedOption,
        info.isUnderscore,
        isShapeResolve = shapesOnly,
        enableTupling  = true
      )

    def resolveConstructorProxies(srrs: Array[ScalaResolveResult]): Array[ScalaResolveResult] = {
      def tryResolveSpecificProxies: Array[ScalaResolveResult] =
        if (srrs.length != 1) srrs
        else {
          val element = srrs.head.element

          val targetDesignator = element match {
            case Typeable(tpe) =>
              val widened = tpe.tryExtractDesignatorSingleton
              widened.extractDesignated(expandAliases = true)
            case _             => None
          }

          targetDesignator match {
            case Some(obj: ScObject) if obj.allFunctionsByName(CommonNames.Apply).isEmpty =>
              val proc           = processor(name = obj.name, kinds = Set(ResolveTargets.CLASS))
              val companionClass = obj.baseCompanion
              companionClass.foreach(proc.execute(_, ScalaResolveState.withImportsUsed(srrs.head.importsUsed)))

              val proxies = proc.candidates

              if (proxies.nonEmpty) proxies
              else                  srrs
            case _ => srrs
          }
        }

      val isExplicitApplyReference = name == CommonNames.Apply

      if (!reference.isInScala3Module) srrs
      else if (!inMethodCallContext(reference) && !isExplicitApplyReference) srrs
      else if (srrs.isEmpty) {
        val amendedRef = reference match {
          case ScReferenceExpression.withQualifier(qual: ScReferenceExpression) if isExplicitApplyReference =>
            qual
          case Parent(sugarCall: ScSugarCallExpr) if isExplicitApplyReference =>
            sugarCall.thisExpr.filterByType[ScReferenceExpression].getOrElse(reference)
          case _ =>
            reference
        }

        val isNonCallApplyReference = isExplicitApplyReference && !inMethodCallContext(reference)
        val proc    = processor(name = amendedRef.refName, kinds = Set(ResolveTargets.CLASS))
        val proxies = doResolve(amendedRef, proc)

        if (proxies.nonEmpty && (amendedRef ne reference)) {
          /**
           * This is the case, where constructor proxy is accessed with an explicit .apply call
           * or reference (e.g. `Test.apply`),
           * to avoid having an unresolved reference to synthetic object, mark it with the
           * [[ReferenceExpressionResolver.ConstructorProxyHolderKey]] key.
           */
          import ReferenceExpressionResolver.ConstructorProxyHolderKey
          reference.qualifier.foreach(_.putUserData(ConstructorProxyHolderKey, true))
        }

        // For non-call explicit .apply references (e.g. `Test.apply` without calling it),
        // replace the class resolve results with constructor resolve results so that the
        // type is computed as a function type (e.g. Int => Test) instead of the class type.
        if (isNonCallApplyReference) {
          proxies.flatMap[ScalaResolveResult] { srr =>
            srr.element match {
              case cls: PsiClass =>
                val constructors = cls.constructors
                if (constructors.nonEmpty)
                  constructors.map(cons =>
                    new ScalaResolveResult(cons, ScSubstitutor.empty, srr.importsUsed, parentElement = Some(cls))
                  )
                else Seq(srr)
              case _ => Seq(srr)
            }
          }
        } else proxies
      } else if (inMethodCallContext(reference)) tryResolveSpecificProxies
      else srrs
    }

    def assignmentResolve(): Array[ScalaResolveResult] = {
      val clauses = Seq(InvocationClause(args = argumentsOf(reference).toOption))

      val assignProcessor =
        new MethodResolveProcessor(
          reference,
          reference.refName.init,
          clauses,
          prevInfoTypeParams,
          isShapeResolve = shapesOnly,
          enableTupling  = true
        )

      doResolve(reference, assignProcessor)
        .map(_.copy(isAssignment = true))
    }



    def resolveAsConstructorProxyHolder(): Array[ScalaResolveResult] = {
      // In Scala 3, `Foo.apply(...)` where `Foo` is a class without a companion object
      // uses "universal apply" (constructor proxies). The qualifier `Foo` should resolve
      // to the class itself, even though CLASS is not normally in the expression-position kinds.
      val isQualifierOfApplyRef = reference.getContext match {
        case ref: ScReferenceExpression =>
          ref.refName == CommonNames.Apply && ref.qualifier.contains(reference)
        case infix: ScInfixExpr =>
          reference == infix.left && infix.operation.refName == CommonNames.Apply
        case _ => false
      }

      if (isQualifierOfApplyRef) {
        val proc = processor(name = name, kinds = Set(ResolveTargets.CLASS))
        doResolve(reference, proc)
      } else Array.empty
    }

    val result = {
      val initialResolve = doResolve(reference, processor())
      val withProxies    = resolveConstructorProxies(initialResolve)

      def isApplicable(srr: ScalaResolveResult): Boolean =
        srr.isApplicable() ||
          // resolve constructors (for Universal Apply) even if the arguments are not applicable
          srr.element.asOptionOf[PsiMethod].exists(_.isConstructor)

      if (withProxies.exists(isApplicable)) withProxies
      else                                  doResolve(reference, processor(), tryThisQualifier = true)
    }

    val resultOrProxyHolder =
      if (result.isEmpty && reference.isInScala3Module) resolveAsConstructorProxyHolder()
      else result

    val resolveAssignment: Boolean =
      resultOrProxyHolder.isEmpty &&
        context.is[ScInfixExpr, ScMethodCall] &&
        name.endsWith("=") &&
        !name.startsWith("=") &&
        !Seq("!=", "<=", ">=").contains(name) &&
        !name.exists(_.isLetterOrDigit)

    if (resolveAssignment) assignmentResolve()
    else                   resultOrProxyHolder
  }

  def doResolve(
    ref:                ScReferenceExpression,
    proc:               BaseProcessor,
    accessibilityCheck: Boolean = true,
    tryThisQualifier:   Boolean = false,
  ): Array[ScalaResolveResult] =
    doResolve(ref, proc, accessibilityCheck, tryThisQualifier, None)

  private def doResolve(
    ref:                ScReferenceExpression,
    proc:               BaseProcessor,
    accessibilityCheck: Boolean,
    tryThisQualifier:   Boolean,
    contextInfo:        Option[InvocationInfo]
  ): Array[ScalaResolveResult] = {
    implicit val context: Context = Context(ref)

    val info = contextInfo.getOrElse(getInvocationInfo(ref, ref))

    val isShape = proc match {
      case m: MethodResolveProcessor => m.isShapeResolve
      case _                         => false
    }

    def resolveUnqualified(): Array[ScalaResolveResult] =
      ref.getContext match {
        case ScSugarCallExpr(operand, operation, _) if ref == operation =>
          processQualifier(operand)
        case (gc: ScGenericCall) childOf ScInfixExpr(lhs, ref, _) if ref == gc.referencedExpr =>
          processQualifier(lhs)
        case _ =>
          resolveUnqualifiedExpression()
          proc.candidates
      }

    def resolveUnqualifiedExpression(): Unit = {
      @tailrec
      def treeWalkUp(@Nullable place: PsiElement, @Nullable lastParent: PsiElement, state: ResolveState): Unit = {
        if (place == null) return

        val newState = place match {
          /** An extension method `f` can be referenced by a simple identifier (and rewritten by the compiler to the qualified form)
           * if it is called from inside the body of an extension method `g`, which is defined in the same collective extension.
           * To support resolution of such cases, we store information about enclosing extension in the resolve state.
           */
          case fdef: ScFunction => fdef.extensionMethodOwner.fold(state)(state.withExtensionContext)
          case (cc: ScCaseClause) & Parent(Parent(m: ScMatch)) =>
            if (cc.pattern.exists(pat => isContextAncestor(pat, ref, true))) {
              //Don't trigger pattern type inference, when resolving references inside patterns.
              //Avoids recursion related problems.
              state
            } else {
              //@TODO: partial functions as well???
              val subst = PatternTypeInference.doForMatchClause(m, cc)
              val oldSubst = state.matchClauseSubstitutor
              state.withMatchClauseSubstitutor(oldSubst.followed(subst))
            }
          case _ => state
        }

        if (!place.processDeclarations(proc, newState, lastParent, ref)) return

        place match {
          case _: ScTemplateBody | _: ScExtendsBlock => //template body and inherited members are at the same level
          case enum: ScEnum                          =>
            if (!enum.fakeCompanionModule.forall(_.processDeclarations(proc, state, lastParent, place))) return
          case _                                     => if (!proc.changedLevel) return
        }

        treeWalkUp(place.getContext, place, newState)
      }

      val context = ref.getContext

      val contextElement = (context, proc) match {
        case (x: ScAssignment, _) if x.leftExpression == ref => Some(context)
        case (_, _: DependencyProcessor)                     => None
        case (_, _: CompletionProcessor)                     => Some(ref)
        case _                                               => None
      }

      contextElement.foreach(processAssignment)
      treeWalkUp(ref, null, ScalaResolveState.empty)
    }

    def processAssignment(assign: PsiElement): Unit = assign.getContext match {
      //trying to resolve naming parameter
      case args: ScArgumentExprList =>
        args.getContext match {
          case invocation: MethodInvocation =>
            processMethodAssignment(args, invocation)
          case invocation: ConstructorInvocationLike =>
            processConstructorReference(args, invocation, assign, args.invocationCount - 1)
          case _ =>
        }
      case tuple: ScTuple => tuple.getContext match {
        case infix @ ScInfixExpr.withAssoc(_, operation, `tuple`) =>
          processAnyAssignment(tuple.exprs, infix, operation)
        case _ =>
      }
      case p: ScParenthesisedExpr => p.getContext match {
        case infix@ScInfixExpr.withAssoc(_, operation, `p`) =>
          processAnyAssignment(p.innerElement.toSeq, infix, operation)
        case _ =>
      }
      case _ =>
    }

    def processMethodAssignment(
      args:      ScArgumentExprList,
      call:      MethodInvocation
    ): Unit =
      args.callReference.foreach { reference =>
        val isNamedParametersEnabled = call match {
          case call: ScMethodCall => call.isNamedParametersEnabledEverywhere
          case _                  => false
        }

        processAnyAssignment(
          args.exprs,
          call,
          reference,
          args.invocationCount - 1,
          isNamedParametersEnabled
        )
      }

    def processAnyAssignment(
      exprs:                    Seq[ScExpression],
      call:                     MethodInvocation,
      callReference:            ScReferenceExpression,
      index:                    Int = 0,
      isNamedParametersEnabled: Boolean = false
    ): Unit = {
      val refName = ref.refName

      def addParamForApplyDynamicNamed(): Unit = proc match {
        case _: CompletionProcessor =>
        case _ =>
          proc.execute(
            createParameterFromText(refName + ": Any", ref),
            ScalaResolveState.withNamedParam
          )
      }

      def processNamedParameterOf(result: ScalaResolveResult, index: Int): Unit = result.element match {
        case _: ScFunction if isApplyDynamicNamed(result)               => addParamForApplyDynamicNamed()
        case _ if call.applyOrUpdateElement.exists(isApplyDynamicNamed) => addParamForApplyDynamicNamed()
        case fun: ScMethodLike =>
          val substitutor = result.substitutor

          proc match {
            case completionProcessor: CompletionProcessor =>
              collectNamedCompletions(fun.parameterList, completionProcessor, substitutor, exprs, index)
            case _ =>
              getParamByName(fun, refName, index).foreach { param =>
                val rename =
                  if (!equivalent(param.name, refName)) param.deprecatedName
                  else                                  None

                val state = ScalaResolveState
                  .withSubstitutor(substitutor)
                  .withNamedParam
                  .withRename(rename)

                proc.execute(param, state)
              }
          }
        case _: FakePsiMethod => //todo: ?
        case method: PsiMethod if isNamedParametersEnabled =>
          val state = ScalaResolveState
            .withSubstitutor(result.substitutor)
            .withNamedParam

          method.parameters.foreach {
            proc.execute(_, state)
          }
        case _ =>
      }

      def tryProcessApplyMethodArgs(): Unit = {
        @tailrec
        def traverseInvokedExprs(call: ScExpression, dropped: Int): Unit = call match {
          case mc: MethodInvocation =>
            val tp            = mc.`type`().getOrAny

            val applyResolves = mc.resolveApplyOrUpdateMethod(
              mc,
              tp,
              shapesOnly    = false,
              withImplicits = true
            )

            applyResolves.foreach(processNamedParameterOf(_, dropped))
            if (proc.candidates.isEmpty) traverseInvokedExprs(mc.getEffectiveInvokedExpr, dropped + 1)
          case _ => ()
        }
        traverseInvokedExprs(call.getEffectiveInvokedExpr, 0)
      }

      for (variant <- callReference.multiResolveScala(false)) {
        processNamedParameterOf(variant, index)
        // Consider named parameters of apply method; see SCL-2407
        variant.innerResolveResult.foreach(processNamedParameterOf(_, index))
      }
      // Check if argument clause is actually an apply method invocation SCL-17892
      if (proc.candidates.isEmpty) tryProcessApplyMethodArgs()
    }

    def processConstructorReference(
      args:          ScArgumentExprList,
      invocation:    ConstructorInvocationLike,
      assign:        PsiElement,
      index:         Int
    ): Unit = {

      def processConstructor(
        typeable: Typeable
      )(
        isTargetClass: ScConstructorOwner => Boolean
      )(
        isAcceptableConstructor: ScFunction => Boolean
      ): Unit = for {
        scType <- typeable.`type`().toOption
        (clazz, subst) <- scType.extractClassType
      } {
        if (!clazz.is[ScTemplateDefinition] && clazz.annotationType) {
          proc match {
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
              } proc.execute(method, ScalaResolveState.empty)
          }
        } else {
          val argumentsByClause = invocation.arguments.map(_.exprs)
          val clauses           = argumentsByClause.map(args => InvocationClause(args = args.toOption))

          val processor = new MethodResolveProcessor(
            invocation,
            "this",
            clauses,
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
                val isFunction = method.is[ScFunction]
                proc match {
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
                      parameter <- getParamByName(method, refName, argumentsByClause.indexOf(args))

                      name = if (isFunction && !equivalent(parameter.name, refName))
                        parameter.deprecatedName.map(clean)
                      else
                        None
                    } proc.execute(
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

    def processQualifier(qualifier: ScExpression): Array[ScalaResolveResult] = {
      ProgressManager.checkCanceled()

      qualifier.getNonValueType() match {
        case Right(tpt @ ScTypePolymorphicType(internal, tp)) if tp.nonEmpty &&
          !internal.is[ScMethodType, UndefinedType] /* optimization */ =>
          val substed = tpt.abstractOrLowerTypeSubstitutor(context)(internal)
          processType(substed, qualifier)
          if (proc.candidates.nonEmpty) return proc.candidates
        case _ =>
      }

      qualifier.`type`().toOption match {
        case Some(tp) => processType(tp, qualifier)
        case _        => proc.candidates
      }
    }

    def explicitApplyReferenceResolve(found: Array[ScalaResolveResult]): Array[ScalaResolveResult] = {
      val maybeExplicitApplyRefAndContextInfo =
        if (ref.refName != CommonNames.Apply
          && inMethodCallContext(ref)
          && found.length == 1
          && !ref.startsWithToken(ScalaTokenTypes.tUNDER)
        ) {
          val srr                   = found.head
          val hasParams             = srr.elementHasParameters
          val hasTypeParams         = srr.elementHasTypeParameters
          val headClause            = info.invocationClauses.headOption
          val hasArgs               = headClause.exists(_.args.nonEmpty)
          val hasMismatchedTypeArgs = !hasTypeParams && headClause.exists(_.targs.nonEmpty)

          if (!hasParams || srr.name == CommonNames.Apply) {
            if (hasMismatchedTypeArgs) {
              // the case when type arguments belong to apply method
              // foo[A](10) -> foo.apply[A](1)
              val res = createRef(ref.getContext, s"${ref.getText}.apply") -> info
              Option(res)
            } else if (hasArgs) {
              // the case when potential type arguments belong to the initial method invocation
              // foo[A](10) -> foo[A].apply(10)
              val invokedExpr               = info.invokedExpr.getOrElse(ref)
              val headClauseWithoutTypeArgs = headClause.get.copy(targs = None)

              val newInfo =
                info.copy(
                  invocationClauses = headClauseWithoutTypeArgs +: info.invocationClauses.tail
                )
              val res =
                createRef(invokedExpr.getContext, s"(${invokedExpr.getText}).apply") ->
                  newInfo

              Option(res)
            }
            else None
          } else None
        } else None

      maybeExplicitApplyRefAndContextInfo match {
        case Some((applyRef, info)) =>
          val resolvedWithApplyRef =
            doResolve(
              applyRef,
              updateResolveProcessor(proc)(_.copy(refName = CommonNames.Apply)),
              accessibilityCheck,
              tryThisQualifier,
              contextInfo = Option(info)
            )

          if (resolvedWithApplyRef.isEmpty) found
          else {
            val prevSrr  = found.head
            val innerSrr = prevSrr.innerResolveResult.getOrElse(prevSrr)

            val res = resolvedWithApplyRef.map { actualSrr =>
              actualSrr.copy(
                innerResolveResult = innerSrr.toOption,
                parentElement      = innerSrr.element.toOption
              )
            }
            res
          }
        case None => found
      }
    }

    def shouldTryImplicitConversions(cands: Array[ScalaResolveResult]) =
      cands.isEmpty || (!isShape && cands.forall(!_.isApplicable()))

    def processType(aType: ScType, qualifier: ScExpression): Array[ScalaResolveResult] = {
      val (fromType, matchSubst) = qualifier match {
        case ref: ScReferenceExpression =>
          val srr = ref.bind()
          val subst = srr.fold(ScSubstitutor.empty)(_.matchClauseSubstitutor)

          val tpe = srr match {
            case Some(ScalaResolveResult(_: ScSelfTypeElement, _)) => aType
            case Some(r @ ScalaResolveResult(b: ScTypedDefinition, _)) if b.isStable =>
              r.fromType match {
                case Some(fT) => ScProjectionType(fT, b)
                case None     => ScalaType.designator(b)
              }
            case _ => aType
          }
          (tpe, subst)
        case _ => (aType, ScSubstitutor.empty)
      }

      val state = fromType match {
        case ScDesignatorType(_: PsiPackage) => ScalaResolveState.empty
        case _                               => ScalaResolveState.withFromType(fromType)
      }

      proc.processType(aType, qualifier, state.withSubstitutor(matchSubst))

      val candidates = proc.candidates

      aType match {
        case d: ScDesignatorType if d.isStatic => return candidates
        case ScDesignatorType(_: PsiPackage)   => return candidates
        case _                                 =>
      }

      val withImplicitConversion = proc match {
        case cp: CompletionProcessor => cp.withImplicitConversions
        case _                       => false
      }

      val withExplicitApply =
        if (candidates.forall(!_.isApplicable())) explicitApplyReferenceResolve(candidates)
        else                                      candidates

      if (shouldTryImplicitConversions(withExplicitApply) || withImplicitConversion) {
        val procForConversions = updateResolveProcessor(proc)(_.copy(noImplicitsForArgs = candidates.nonEmpty))

        ImplicitConversionResolveResult.processImplicitConversionsAndExtensions(
          targetNameForImplicitProcessor(proc),
          ref,
          procForConversions,
          noImplicitsForArgs = candidates.nonEmpty,
          forCompletion      = procForConversions.is[CompletionProcessor]
        )(_.withImports.withImplicitConversionResultType.withType)(qualifier)

        val fromImplicits = (procForConversions, procForConversions.candidates) match {
          case (methodProcessor: MethodResolveProcessor, Array()) if conformsToDynamic(fromType, ref.resolveScope) =>
            val dynamicProcessor = dynamicResolveProcessor(ref, qualifier, methodProcessor)
            dynamicProcessor.processType(fromType, qualifier, state)
            dynamicProcessor.candidates
          case (_, cands) => cands
        }

        //If none of the candidates from implicit conversions/extensions are applicable,
        //and simple resolve produced some (inapplicable) candidates, return them.
        if (fromImplicits.forall(!_.isApplicable()) && withExplicitApply.nonEmpty) withExplicitApply
        else                                                                       fromImplicits
      } else withExplicitApply
    }

    def updateResolveProcessor(
      processor: BaseProcessor
    )(
      update: MethodResolveProcessor => BaseProcessor
    ): BaseProcessor = processor match {
      case mrp: MethodResolveProcessor => update(mrp)
      case other                       => other
    }

    def targetNameForImplicitProcessor(processor: BaseProcessor): Option[String] =
      processor match {
        case _: CompletionProcessor      => None
        case processor: ResolveProcessor => Option(processor.name) // See SCL-2934.
        case _                           => Option(ref.refName)
      }

    if (!accessibilityCheck) proc.doNotCheckAccessibility()

    var res = ref.qualifier match {
      case None =>
        val unqualified = resolveUnqualified()
        if (unqualified.forall(!_.isApplicable()))
          explicitApplyReferenceResolve(unqualified)
        else unqualified
      case Some(superQ: ScSuperReference) =>
        ResolveUtils.processSuperReference(superQ, proc, ref)
      case Some(q) => processQualifier(q)
    }

    if (accessibilityCheck && res.isEmpty) {
      res = doResolve(
        ref,
        proc,
        accessibilityCheck = false,
        tryThisQualifier   = false,
        contextInfo        = Option(info)
      )
    }

    val isInfixOp = ref.getContext match {
      case inf: ScInfixExpr => inf.operation == ref
      case _                => false
    }

    if (
      res.nonEmpty &&
        res.forall(!_.isValidResult) &&
        ref.qualifier.isEmpty &&
        tryThisQualifier &&
        !isInfixOp
    ) {
      val thisExpr = createRef(ref.getContext, s"this.${ref.getText}")

      res = doResolve(
        thisExpr,
        proc,
        accessibilityCheck,
        tryThisQualifier = false,
        contextInfo      = Option(info)
      )
    }

    res
  }

  private def createRef(context: PsiElement, text: String): ScReferenceExpression =
    ScalaPsiElementFactory.createExpressionWithContextFromText(text, context)
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
