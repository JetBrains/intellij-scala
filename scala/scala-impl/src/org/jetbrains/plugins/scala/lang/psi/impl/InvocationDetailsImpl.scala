package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.{PsiElement, PsiMethod, PsiTypeParameterList}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, ScConstructorInvocation, ScMethodLike}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, InvocationDetails, InvocationDetailsOwner}
import org.jetbrains.plugins.scala.lang.psi.api.InvocationDetails._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScSignatureClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ContextFunctionType, FunctionType, ParameterizedType, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.PsiElementForExpectedTypesEx
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.collection.immutable.LongMap

/**
 * Works out the details of the call an expression is part of, see [[InvocationDetails]].
 */
private[psi] object InvocationDetailsImpl {
  private type InvocationDetailsExpr = InvocationDetailsOwner with ScExpression
  
  def of(invocation: ConstructorInvocationLike): InvocationDetails =
    cachedInUserData(
      "InvocationDetailsImpl.constructorCall",
      invocation,
      BlockModificationTracker(invocation)
    ) {
      detailsOfConstructorInvocation(invocation)
    }

  def of(owner: InvocationDetailsOwner): Option[InvocationDetails] = owner match {
    case invocation: ConstructorInvocationLike => Some(of(invocation))
    case expression: ScExpression =>
      invocationsInExpr(syntaxRootOf(expression)).get(expression)
    case _ => None
  }


  private def invocationsInExpr(syntaxRoot: InvocationDetailsExpr): Map[ScExpression, InvocationDetails] =
    cachedInUserData(
      "InvocationDetailsImpl.callSegments",
      syntaxRoot,
      BlockModificationTracker(syntaxRoot)
    ) {
      detailsOf(syntaxRoot).iterator.flatMap(d => d.origin.asOptionOfUnsafe[ScExpression].map(_ -> d)).toMap
    }

  private def detailsOfConstructorInvocation(invocation: ConstructorInvocationLike): InvocationDetails = {
    val (target, anchor) = invocation match {
      case call: ScConstructorInvocation => (call.reference.flatMap(_.bind()), call.typeElement)
      case call: ScSelfInvocation        => (call.multiResolve.headOption, call.thisElement)
    }
    val call = Call.initial(invocation, target, None, anchor, isConstructor = true)
    val applicability = for {
      result <- target
      method <- result.element.asOptionOf[PsiMethod]
    } yield Compatibility.checkConstructorApplicability(invocation, method, result)(invocation.projectContext)

    val inferred = applicability.toSeq.flatMap { case (tpe, _, _) =>
      val typeParameters = call.remaining.collect { case t: TypeSignature => t.parameters }.flatten
      tpe.inferValueType match {
        case ParameterizedType(_, args) => typeParameters.zip(args).map { case (parameter, arg) => parameter.typeParamId -> arg }
        case _ => Seq.empty
      }
    }
    val withInferredTypes = call.copy(inferred = LongMap.from(inferred))
    val withTypeArguments = invocation.typeArgList.fold(withInferredTypes)(withInferredTypes.typeArguments(_, anchor))
    val applied = invocation.arguments
      .zipWithIndex
      .foldLeft(withTypeArguments) {
        case (call, (args, clauseIndex)) =>
          val prepared = call.omittedBefore(isType = false, args.isUsing)
          prepared.nextValue
            .map { signature =>
              val subst = prepared.substitution(prepared.inferred)
              val parameters = signature.parameters.map(substituteParameter(_, subst))
              val expressions: Seq[Compatibility.Expression] = args.exprs
              val plain = Compatibility.checkMethodApplicability(parameters, expressions, withImplicits = true, shapesOnly = false)
              val isTupled = plain.problems.nonEmpty && parameters.size == 1 && !parameters.head.isDefault && !parameters.head.isRepeated
              val tupled = if (isTupled)
                ScalaPsiUtil.tupled(expressions, invocation).map { tuple =>
                  Compatibility.checkMethodApplicability(parameters, tuple, withImplicits = true, shapesOnly = false)
                }.filter(_.problems.isEmpty)
              else None
              val matched = tupled
                .map(result => args.exprs.map(_ -> result.matched.head.parameter))
                .orElse(invocation.matchedParametersByClauses.lift(clauseIndex))
                .getOrElse(Seq.empty)
              prepared.valueArguments(args, matched, tupled.nonEmpty, prepared.anchor)
            }
            .getOrElse(prepared)
            .copy(anchor = args)
      }
    applied.result(
      initialTypes = applicability.toSeq.map(_._1),
      initialImplicits = applicability.toSeq.flatMap(_._3)
    )
  }

  private def detailsOf(syntaxRoot: InvocationDetailsExpr): Seq[InvocationDetails] = {
    def start(owner: InvocationDetailsExpr, target: Option[ScalaResolveResult], receiver: Option[ScExpression],
              anchor: PsiElement, apply: Boolean = false, update: Boolean = false): Call = {
      val constructor = target.exists(_.element.asOptionOf[PsiMethod].exists(_.isConstructor))
      Call.initial(owner, target, receiver, anchor, isConstructor = constructor,
        isApply = apply && !constructor, isUpdate = update, isUniversalApply = constructor)
    }

    def visit(expression: ScExpression): Calls = expression match {
      case parens: ScParenthesisedExpr =>
        parens.innerElement.fold(Calls())(visit).mapCurrent(_.copy(anchor = parens))
      case generic: ScGenericCall =>
        visit(generic.referencedExpr)
          .mapCurrent(_.omittedBefore(isType = true, isUsing = false))
          .continueWhen(_.nextType.isDefined) {
            val target =
              generic.referencedExpr
                .getNonValueType().toOption.toSeq
                .flatMap { tpe =>
                  generic.resolveApplyOrUpdateMethod(generic.referencedExpr, tpe, shapesOnly = false, withImplicits = true)
                }
                .headOption
                .map(_.mostInnerResolveResult)
            start(generic, target, Some(generic.referencedExpr), generic.referencedExpr, apply = true)
          }
          .mapCurrent(_.typeArguments(generic.typeArgs, generic.referencedExpr).at(generic))
      case invocation: MethodInvocation =>
        val previous = visit(invocation.getInvokedExpr)
        val using = invocation.argsElement.asOptionOf[ScArgumentExprList].exists(_.isUsing)
        val prepared = previous.mapCurrent(_.omittedBefore(isType = false, isUsing = using))
        val operator = invocation.is[ScPrefixExpr, ScPostfixExpr]
        prepared.continueWhen(c => c.nextValue.isDefined || operator, invocation.implicitArgumentsOfInvokedExpression) {
            val target = invocation.target
              .orElse(invocation.getEffectiveInvokedExpr.asOptionOf[ScReferenceExpression].flatMap(_.bind()))
              .orElse(prepared.current.flatMap(_.resultType).flatMap { tpe =>
                invocation.getInvokedExpr.resolveApplyOrUpdateMethod(
                  invocation.getInvokedExpr, tpe, shapesOnly = false, withImplicits = true
                ) match {
                  case Array(target) => Some(target.mostInnerResolveResult)
                  case _ => None
                }
              })
            val sugared = invocation.isApplyOrUpdateCall || prepared.current.isDefined ||
              !invocation.getEffectiveInvokedExpr.is[ScReferenceExpression, ScGenericCall]
            val receiver = if (sugared) Some(invocation.getInvokedExpr) else invocation.thisExpr
            start(invocation, target, receiver, invocation.getInvokedExpr,
              apply = sugared && !invocation.isUpdateCall, update = invocation.isUpdateCall)
          }
          .mapCurrent { call =>
            val ready = call.omittedBefore(isType = false, isUsing = using)
            val matched = if (invocation.target.isDefined) invocation.matchedParameters else
              ready.nextValue.toSeq.flatMap { signature =>
                Compatibility.checkMethodApplicability(
                  signature.parameters.map(substituteParameter(_, ready.substitution(ready.inferred))),
                  invocation.argumentExpressions, withImplicits = true, shapesOnly = false
                ).matched.map(m => m.argument -> m.parameter)
              }
            val applied = ready
              .valueArguments(invocation.argsElement, matched, invocation.isAutoTupling, invocation.getInvokedExpr)
              .at(invocation)
            val receiver = if (invocation.is[ScMethodCall]) applied.details.thisExpr else invocation.thisExpr
            applied.copy(
              details = applied.details.copy(thisExpr = receiver),
              inferred = applied.inferred ++ invocation.matchedTypeParameters.map { case (tpe, parameter) => parameter.typeParamId -> tpe }
            )
          }
      case reference: ScReferenceExpression =>
        val call = reference.bind().map(_.mostInnerResolveResult)
          .filter(r => r.element.is[PsiMethod, ScSyntheticFunction] && !r.isNamedParameter)
          .map(target => start(reference, Some(target), reference.qualifier, reference).at(reference))
        Calls(current = call)
      case assignment: ScAssignment =>
        assignment.leftExpression match {
          case invocation: MethodInvocation =>
            visit(invocation)
              .mapCurrent(call => call.copy(details = call.details.copy(origin = assignment)))
          case reference: ScReferenceExpression =>
            val call = assignment.resolveAssignment.filter(_.element.is[PsiMethod]).map { target =>
              val call = start(assignment, Some(target), reference.qualifier, reference)
                .omittedBefore(isType = false, isUsing = false)
              val applied = call.nextValue.fold(call) { signature =>
                val arguments = assignment.rightExpression.toSeq.zip(signature.parameters)
                call.valueArguments(assignment.rightExpression.getOrElse(assignment), arguments, autoTupling = false, reference)
              }
              applied.copy(details = applied.details.copy(isAssignmentCall = true))
            }
            Calls(current = call)
          case _ =>
            Calls()
        }
      case EtaExpansionSection(section, binding) =>
        visit(binding).mapCurrent { call =>
          call.copy(
            details = call.details.copy(origin = section, isPartiallyApplied = true),
            expressionsReversed = section :: call.expressionsReversed
          )
        }
      case _ =>
        Calls()
    }

    visit(syntaxRoot).finish().completedReversed.reverse
  }

  private case class Calls(completedReversed: List[InvocationDetails] = Nil, current: Option[Call] = None) {
    def mapCurrent(f: Call => Call): Calls = copy(current = current.map(f))

    def finish(trailingImplicits: Seq[ImplicitArgumentsClause] = Seq.empty): Calls =
      current.fold(this)(call => Calls(call.result(trailingImplicits = trailingImplicits) :: completedReversed))

    def continueWhen(predicate: Call => Boolean, receiverImplicits: => Seq[ImplicitArgumentsClause] = Seq.empty)(next: => Call): Calls =
      if (current.exists(predicate)) this
      else {
        val call = next
        finish(receiverImplicits).copy(current = Some(call))
      }
  }

  private case class Details(
    origin: InvocationDetailsOwner,
    target: Option[ScalaResolveResult],
    thisExpr: Option[ScExpression],
    argumentClauses: Seq[ArgumentClause],
    isApply: Boolean,
    isUniversalApply: Boolean,
    isConstructorInvocation: Boolean,
    isUpdate: Boolean,
    isAssignmentCall: Boolean,
    isPartiallyApplied: Boolean
  ) extends InvocationDetails

  private sealed trait Signature
  private case class TypeSignature(target: ArgumentClauseTarget.Type, parameters: Seq[TypeParameter]) extends Signature
  private case class ValueSignature(target: ArgumentClauseTarget.Value, parameters: Seq[Parameter],
                                    isImplicit: Boolean = false, hasUsing: Boolean = false) extends Signature

  private def signatureOf(result: ScalaResolveResult, selfInvocation: Boolean): Seq[Signature] = {
    def types(@Nullable list: PsiTypeParameterList): Seq[TypeSignature] =
      Option(list).toSeq
        .filter(_.getTypeParameters.nonEmpty)
        .map(list =>
          TypeSignature(
            ArgumentClauseTarget.TypeParameters(list),
            list.getTypeParameters.toSeq.map(TypeParameter(_))
          )
        )

    def values(clause: ScParameterClause): ValueSignature =
      ValueSignature(ArgumentClauseTarget.ScalaParameters(clause), clause.getSmartParameters, clause.isImplicit, clause.hasUsingKeyword)

    result.element match {
      case method: ScMethodLike =>
        val classTypes =
          if (method.isConstructor && !selfInvocation) method.getClassTypeParameters.toSeq.flatMap(types)
          else Seq.empty

        classTypes ++ method.effectiveSignatureClauses.map {
          case ScSignatureClause.TypeClause(clause) =>
            TypeSignature(ArgumentClauseTarget.TypeParameters(clause), clause.typeParameters.map(TypeParameter(_)))
          case ScSignatureClause.TermClause(clause) => values(clause)
        }
      case method: PsiMethod =>
        val classTypes =
          if (method.isConstructor && !selfInvocation) types(method.getContainingClass.getTypeParameterList)
          else Seq.empty
        classTypes ++ types(method.getTypeParameterList) :+
          ValueSignature(ArgumentClauseTarget.JavaParameters(method.getParameterList), method.parameters.map(Parameter(_)))
      case function: ScSyntheticFunction =>
        val target = ArgumentClauseTarget.Synthetic(function)
        val typeClause = Option.when(function.typeParameters.nonEmpty)(TypeSignature(target, function.typeParameters.map(TypeParameter(_))))
        typeClause.toSeq ++ function.paramClauses.map(ValueSignature(target, _))
      case _ => Seq.empty
    }
  }

  private object Call {
    def initial(
      origin: InvocationDetailsOwner,
      target: Option[ScalaResolveResult],
      receiver: Option[ScExpression],
      anchor: PsiElement,
      isConstructor: Boolean = false,
      isApply: Boolean = false,
      isUpdate: Boolean = false,
      isUniversalApply: Boolean = false
    ): Call = {
      val details = Details(origin, target, receiver, Seq.empty, isApply, isUniversalApply,
        isConstructor, isUpdate, isAssignmentCall = false, isPartiallyApplied = false)
      val signature = target.toList.flatMap(signatureOf(_, origin.is[ScSelfInvocation]))
      Call(details, signature, anchor)
    }
  }

  /**
   * A single callee and the consecutive syntax nodes which supply its clauses.
   * Consuming a signature shares its remaining tail; applications and expressions are prepended
   * and reversed once when the call is complete.
   */
  private case class Call(
    details: Details,
    remaining: List[Signature],
    anchor: PsiElement,
    appliedReversed: List[(Signature, ArgumentClause)] = Nil,
    expressionsReversed: List[ScExpression] = Nil,
    inferred: LongMap[ScType] = LongMap.empty,
    explicit: LongMap[ScType] = LongMap.empty
  ) {
    private implicit def projectContext: ProjectContext = details.origin.projectContext

    def nextType: Option[TypeSignature] = remaining.headOption.collect { case s: TypeSignature => s }
    def nextValue: Option[ValueSignature] = remaining.headOption.collect { case s: ValueSignature => s }

    def at(expression: InvocationDetailsExpr): Call = copy(
      details = details.copy(origin = expression),
      anchor = expression,
      expressionsReversed = expression :: expressionsReversed
    )

    @tailrec
    final def omittedBefore(isType: Boolean, isUsing: Boolean): Call = remaining match {
      case (_: TypeSignature) :: _ if !isType =>
        omit.omittedBefore(isType, isUsing)
      case (v: ValueSignature) :: _ if v.isImplicit && (isType || v.hasUsing && !isUsing) =>
        omit.omittedBefore(isType, isUsing)
      case _ => this
    }

    private def applied(clause: ArgumentClause): Call = copy(
      remaining = remaining.tail,
      appliedReversed = (remaining.head -> clause) :: appliedReversed
    )

    private def omit: Call = remaining match {
      case (t: TypeSignature) :: _ =>
        applied(TypeClause(None, t.parameters.map(p => TypeArgument(p, TypeParameterType(p), None)))(t.target, anchor))
      case (v: ValueSignature) :: _ =>
        // Implicit clauses are filled after typing all nodes, which also completes their inference.
        applied(EtaExpandedClause(v.parameters)(v.target, anchor))
      case Nil => this
    }

    def typeArguments(args: ScTypeArgs, anchor: PsiElement): Call = nextType.fold(this) { t =>
      val arguments = t.parameters.zipWithIndex.map { case (parameter, i) =>
        val arg = if (args.hasNamedTypeArgs) args.typeArguments.find(_.name.contains(parameter.name)) else args.typeArguments.lift(i)
        val element = arg.flatMap(_.typeElement)
        val tpe = element.flatMap(_.`type`().toOption).getOrElse(TypeParameterType(parameter))
        TypeArgument(parameter, tpe, element)
      }
      applied(TypeClause(Some(args), arguments)(t.target, anchor)).copy(
        explicit = explicit ++ arguments.collect { case arg if arg.isExplicit => arg.parameter.typeParamId -> arg.tpe }
      )
    }

    def valueArguments(args: PsiElement, matched: Seq[(ScExpression, Parameter)], autoTupling: Boolean, anchor: PsiElement): Call =
      nextValue.fold(this) { v =>
        val defaults = v.parameters.filter(p => p.isDefault && !matched.exists(_._2.name == p.name)).flatMap { p =>
          p.paramInCode.flatMap(_.getDefaultExpression).map(_ -> p)
        }
        val hasPlaceholder = matched.exists { case (arg, _) => ScUnderScoreSectionUtil.isUnderscore(arg) }
        applied(ValueClause(args, matched ++ defaults, autoTupling)(v.target, anchor)).copy(
          details = details.copy(isPartiallyApplied = details.isPartiallyApplied || hasPlaceholder)
        )
      }

    def substitution(inferred: LongMap[ScType]): ScSubstitutor =
      details.target.fold(ScSubstitutor.empty)(_.substitutor).followed(ScSubstitutor(inferred ++ explicit))

    def resultType: Option[ScType] = {
      val completed = result()
      val typeArguments = completed.argumentClauses.collect {
        case clause: TypeClause => clause.arguments.map(a => a.parameter.typeParamId -> a.tpe)
      }.flatten
      @tailrec
      def stripClauses(tpe: ScType): ScType = tpe match {
        case poly: ScTypePolymorphicType => stripClauses(poly.internalType)
        case method: ScMethodType => stripClauses(method.result)
        case tpe => tpe
      }
      if (completed.isPartiallyApplied) None
      else expressionsReversed.headOption.flatMap(_.getNonValueType().toOption)
        .map(tpe => substitution(LongMap.from(typeArguments))(stripClauses(tpe)))
    }

    private def argumentTypeInference: LongMap[ScType] = inferred.filterNot {
      // A missing constraint leaves the callee's own type parameter in the mapping. That is
      // only an actual type argument when its declaration encloses this call (e.g. recursion).
      case (id, parameter: TypeParameterType) =>
        parameter.typeParamId == id &&
          !PsiTreeUtil.isContextAncestor(parameter.psiTypeParameter.getOwner, details.origin, false)
      case _ => false
    }

    private def expectedTypeArguments(tpe: Option[ScType], expected: Option[ScType]): LongMap[ScType] = {
      // A parameterless method can be constrained only from above by its expected result.
      // Solve those constraints before falling back to the polymorphic type's lower bounds.
      val inferredFromExpected = for {
        poly <- tpe.collect { case poly: ScTypePolymorphicType if !poly.internalType.is[ScMethodType] => poly }
        expected <- expected
        conformance = poly.undefinedSubstitutor(poly.internalType).isConservativelyCompatible(expected)
        if conformance.isRight
        constraints = conformance.constraints
        initialBounds <- constraints.substitutionBounds(canThrowSCE = false)
        bounds <- InferUtil.constraintsWithTypeParameterBounds(constraints, initialBounds, poly.typeParameters)
          .substitutionBounds(canThrowSCE = false)
      } yield LongMap.from(
        poly.typeParameters
          // Parameters already considered by argument inference use their inferred bounds,
          // even if unconstrained: an expected Either[E, A] must not widen Right(a)'s Nothing to E.
          .filter(p => constraints.isApplicable(p.typeParamId) && !inferred.contains(p.typeParamId))
          .map(p => p.typeParamId -> bounds.substitutor(TypeParameterType(p)))
      )
      inferredFromExpected.getOrElse(LongMap.empty)
    }

    private def inferredTypeArguments(
      types: Seq[ScType],
      implicits: Seq[ImplicitArgumentsClause],
      typeParameters: Seq[TypeParameter],
      fromArguments: LongMap[ScType],
      fromExpected: LongMap[ScType]
    ): LongMap[ScType] = {
      def typeClauses(tpe: ScType): Iterator[ScTypePolymorphicType] = tpe match {
        case poly: ScTypePolymorphicType => Iterator.single(poly) ++ typeClauses(poly.internalType)
        case method: ScMethodType => typeClauses(method.result)
        case _ => Iterator.empty
      }

      // Later nodes have more precise bounds. Argument inference takes precedence over the fallback.
      // Type clauses can follow value clauses, including omitted using clauses.
      val fromBounds = types.reverseIterator.flatMap(typeClauses).foldLeft(fromArguments ++ fromExpected) {
        case (inferred, poly) =>
          val subst = poly.polymorphicTypeSubstitutor
          poly.typeParameters.foldLeft(inferred) { (inferred, parameter) =>
            if (inferred.contains(parameter.typeParamId)) inferred
            else inferred.updated(parameter.typeParamId, subst(TypeParameterType(parameter)))
          }
      }
      implicits.foldLeft(fromBounds) { (inferred, clause) =>
        clause.constraints.substitutionBounds(canThrowSCE = false).fold(inferred) { bounds =>
          inferred ++ typeParameters.filter(bounds.substitutor.isApplicableToTypeParam(_)).map { p =>
            p.typeParamId -> bounds.substitutor(TypeParameterType(p)).removeAbstracts
          }
        }
      }
    }

    private def resolveClauses(
      applications: List[(Signature, ArgumentClause)],
      implicits: Seq[ImplicitArgumentsClause],
      expectedType: Option[ScType],
      subst: ScSubstitutor
    ): List[ArgumentClause] = {
      val expectsFunction = expectedType.exists(FunctionType.isFunctionType)
      val omittedImplicitClauses = applications.count {
        case (signature: ValueSignature, _: EtaExpandedClause) => signature.isImplicit
        case _ => false
      }
      // Interleaved type clauses can leave gaps in the typing cache, and typing a generic
      // call's trailing clauses can overwrite its leading ones. An incomplete sequence
      // therefore cannot be aligned by position; resolve those clauses from their signatures.
      val recordedImplicits = if (implicits.size < omittedImplicitClauses) Nil else implicits.toList

      @tailrec
      def loop(
        remaining: List[(Signature, ArgumentClause)],
        implicits: List[ImplicitArgumentsClause],
        expected: Option[ScType],
        resolvedReversed: List[ArgumentClause]
      ): List[ArgumentClause] = remaining match {
        case Nil => resolvedReversed.reverse
        case (_, clause: TypeClause) :: tail =>
          val arguments = clause.arguments.map { arg =>
            arg.copy(tpe = if (arg.isExplicit) arg.tpe else subst(TypeParameterType(arg.parameter)).removeAbstracts)
          }
          val resolved = TypeClause(clause.origin, arguments)(clause.target, clause.anchor)
          loop(tail, implicits, expected, resolved :: resolvedReversed)
        case (signature: ValueSignature, clause: EtaExpandedClause) :: tail =>
          if (signature.isImplicit && !expected.exists(ContextFunctionType.isContextFunctionType)) {
            val implicitClause = implicits.headOption.getOrElse {
              val method = ScMethodType(
                api.Unit, signature.parameters.map(substituteParameter(_, subst)),
                hasImplicitKW = !signature.hasUsing, hasUsingKW = signature.hasUsing
              )(details.origin.elementScope)
              InferUtil.updateTypeWithImplicitParameters(
                method, details.origin, None, canThrowSCE = false, fullInfo = false
              )._2.head
            }
            val resolved = ImplicitValueClause(implicitClause.args)(clause.target, clause.anchor, implicitClause.constraints)
            loop(tail, implicits.drop(1), expected, resolved :: resolvedReversed)
          } else if (signature.parameters.isEmpty && (details.isConstructorInvocation ||
            clause.target.is[ArgumentClauseTarget.JavaParameters] && !expectsFunction)) {
            val resolved = AutoAppliedClause()(clause.target, clause.anchor)
            loop(tail, implicits, expected, resolved :: resolvedReversed)
          } else {
            val nextExpected = expected.flatMap {
              case ContextFunctionType(result, _) => Some(result)
              case FunctionType(result, _) => Some(result)
              case _ => None
            }
            val resolved = EtaExpandedClause(clause.parameters.map(substituteParameter(_, subst)))(clause.target, clause.anchor)
            loop(tail, implicits, nextExpected, resolved :: resolvedReversed)
          }
        case (_, clause) :: tail =>
          loop(tail, implicits, expected, clause :: resolvedReversed)
      }

      loop(applications, recordedImplicits, expectedType, Nil)
    }

    def result(
      initialTypes: Seq[ScType] = Seq.empty,
      initialImplicits: Seq[ImplicitArgumentsClause] = Seq.empty,
      trailingImplicits: Seq[ImplicitArgumentsClause] = Seq.empty
    ): InvocationDetails = {
      val applications = remaining.foldLeft(this)((call, _) => call.omit).appliedReversed.reverse
      // Finding implicit arguments may type the expression, so keep each pair of reads together.
      val typingResults = expressionsReversed.reverse.map { expression =>
        val tpe = expression.getNonValueType().toOption
        val arguments = expression.findImplicitArguments
        // A synthetic apply/update records the receiver's clauses on the same PSI node.
        // They are passed to the preceding invocation when that call is finished.
        val ownArguments = expression match {
          // A recovered apply has no typing result of its own. Any cached arguments belong
          // to the unsuccessful receiver application, not to the recovered target.
          case invocation: MethodInvocation if details.isApply && invocation.target.isEmpty => Seq.empty
          case invocation: MethodInvocation => arguments.drop(invocation.implicitArgumentsOfInvokedExpression.size)
          case _ => arguments
        }
        (tpe, ownArguments)
      }
      val lastExpression = expressionsReversed.headOption
      val lastType = lastExpression.flatMap(_.getNonValueType().toOption)
      val expected = lastExpression.flatMap(_.expectedType())
      val withExpected = for {
        expression <- lastExpression
        tpe <- lastType
      } yield InferUtil.updateAccordingToExpectedType(tpe, filterTypeParams = false, expected, expression, canThrowSCE = false)

      val types = initialTypes ++ typingResults.flatMap(_._1) ++ withExpected
      val implicits = initialImplicits ++ typingResults.flatMap(_._2) ++ trailingImplicits
      val typeParameters = applications.collect { case (t: TypeSignature, _) => t.parameters }.flatten
      val fromArguments = argumentTypeInference
      val inferred = inferredTypeArguments(types, implicits, typeParameters, fromArguments,
        expectedTypeArguments(lastType, expected))
      val clauses = resolveClauses(applications, implicits, expected, substitution(inferred))

      details.copy(
        argumentClauses = clauses,
        isPartiallyApplied = details.isPartiallyApplied || clauses.exists {
          case v: ArgumentClause.Value => v.isEtaExpanded
          case _ => false
        }
      )
    }
  }

  private def substituteParameter(parameter: Parameter, subst: ScSubstitutor): Parameter =
    parameter.copy(paramType = subst(parameter.paramType), expectedType = subst(parameter.expectedType))

  /**
   * The outermost expression of the syntax `owner` is part of, which is where all the calls of that
   * syntax are worked out at once.
   */
  private def syntaxRootOf(owner: InvocationDetailsExpr): InvocationDetailsExpr = {
    @tailrec
    def loop(candidate: ScExpression, last: InvocationDetailsExpr): InvocationDetailsExpr = candidate.getContext match {
      case genericCall: ScGenericCall if genericCall.referencedExpr == candidate   => loop(genericCall, genericCall)
      case invocation: MethodInvocation if invocation.getInvokedExpr == candidate  => loop(invocation, invocation)
      case parenthesised: ScParenthesisedExpr                                      => loop(parenthesised, last)
      case EtaExpansionSection(section, `candidate`)                               => loop(section, section)
      //the left side of an assignment is the syntax of the call it is part of, `a(i)` of `a(i) = b`
      //and `a.x` of `a.x = b`, while the right side is an argument of that call and syntax of its own
      case assignment: ScAssignment if assignment.leftExpression == candidate      =>
        loop(assignment, assignment)
      case _                                                                       => last
    }

    loop(owner, owner)
  }

  /**
   * The `f _` of an eta expansion the source spells out, as the underscore section and the call it
   * eta expands. An underscore without a call in front of it is a placeholder, the `_` of `f(_)`,
   * which is an argument of a call rather than the syntax of one.
   */
  private object EtaExpansionSection {
    def unapply(expr: PsiElement): Option[(ScUnderscoreSection, ScExpression)] = expr match {
      case section: ScUnderscoreSection => section.bindingExpr.map((section, _))
      case _                            => None
    }
  }
}
