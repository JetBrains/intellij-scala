package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScGivenPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGiven, ScGivenDefinition, ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, _}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt, ScalaLanguageLevel}

import scala.collection.mutable

class MostSpecificUtil(
  place:      PsiElement,
  argsLength: Int
) {
  private implicit def ctx: ProjectContext = place
  private implicit def context: Context = Context(place)

  def mostSpecificForParameterClause(
    cands:                   Set[(ScalaResolveResult, Option[ScParameterClause])],
    isForImplicitResolution: Boolean
  ): Option[ScalaResolveResult] =
    mostSpecificGeneric(
      cands.map { case (r, paramClause) =>
        InnerScalaResolveResult(
          r,
          r.substitutor,
          parameterClause     = paramClause,
          isImplicitCandidate = isForImplicitResolution
        )
      },
      noImplicit = false
    ).map(_.repr)

  def mostSpecificForResolveResult(applicable: Iterable[ScalaResolveResult]): Option[ScalaResolveResult] =
    mostSpecificGeneric(
      applicable.map(r => InnerScalaResolveResult(r, r.substitutor)),
      noImplicit = false
    ).map(_.repr)

  def mostSpecificForImplicitParameters(applicable: Iterable[ScalaResolveResult]): Option[ScalaResolveResult] =
    mostSpecificGeneric(
      applicable.map(r => innerSrrForImplicitCandidate(r, withSubst = true)),
      noImplicit = true
    ).map(_.repr)

  private def innerSrrForImplicitCandidate(
    r:         ScalaResolveResult,
    withSubst: Boolean = false
  ): InnerScalaResolveResult = {
    val subst =
      if (withSubst) r.substitutor
      else           r.implicitScopeType.fold(ScSubstitutor.empty)(ScSubstitutor.apply)

    InnerScalaResolveResult(
      r,
      subst,
      isImplicitCandidate = true
    )
  }

  def nextMostSpecific(rest: Iterable[ScalaResolveResult]): Option[ScalaResolveResult] = {
    nextMostSpecificGeneric(rest.map(innerSrrForImplicitCandidate(_))).map(_.repr)
  }

  def isMoreSpecific(r1: ScalaResolveResult, r2: ScalaResolveResult): Boolean =
    isMoreSpecific(innerSrrForImplicitCandidate(r1), innerSrrForImplicitCandidate(r2), checkImplicits = false)

  /**
   * @param parameterClause See [[MethodResolveProcessor]].
   *                        Used for overloading resolution in scala 3.
   *
   * @TODO: callByNameImplicit seems to be always == false, I've checked git history all the way
   *        back to 2018, and it looks like it was always this way. This is obviously incorrect,
   *        but probably rather low impact.
   */
  private case class InnerScalaResolveResult(
    repr:                ScalaResolveResult,
    substitutor:         ScSubstitutor,
    parameterClause:     Option[ScParameterClause] = None,
    callByNameImplicit:  Boolean                   = false,
    isImplicitCandidate: Boolean                   = false,
  ) {
    def element: PsiNamedElement         = repr.element

    def implicitConversionClass: Option[PsiClass] =
      for {
        conversion <- repr.implicitConversion
        member     <- conversion.element.nameContext.asOptionOf[ScMember]
        psiClass   <- Option(member.containingClass)
      } yield psiClass

    def paramsOrCandidateType(
      tp:       ScType,
      undefine: Boolean
    ): Either[Seq[Parameter], ScType] = {
      def parameters(paramsFromType: Seq[Parameter]): Seq[Parameter] =
        parameterClause.fold(paramsFromType)(_.parameters.map(Compatibility.toParameter(_, substitutor)))

      tp match {
        case ScMethodType(_, params, _) =>
          Left(parameters(params))
        case ScTypePolymorphicType(ScMethodType(_, params, _), typeParams) =>
          if (!undefine) Left(parameters(params))
          else {
            val s = ScSubstitutor.bind(typeParams)(UndefinedType(_))
            Left(
              parameters(params)
                .map(
                  p => p.copy(paramType = s(p.paramType))
                )
            )
          }
        case ScTypePolymorphicType(internal, typeParams) =>
          val existentialSubst = new ExistentialAbstractionBuilder(typeParams).substitutor
          val usedNames        = mutable.Set.empty[String]
          val argumentsBuilder = List.newBuilder[ScExistentialArgument]

          val substedInternal = existentialSubst(internal).visitRecursively {
            case arg: ScExistentialArgument =>
              arg.initialize()
              val name = arg.name

              if (!usedNames.contains(name) && typeParams.exists(_.name == name)) {
                usedNames.add(name)
                argumentsBuilder += arg
              }
            case _ => ()
          }

          Right(
            ScExistentialType(
              substedInternal,
              Option(argumentsBuilder.result()),
              doNotSimplify = true
            )
          )
        case _ => Right(tp)
      }
    }
  }

  private class ExistentialAbstractionBuilder(tparams: Seq[TypeParameter]) {
    private val typeParamIds                                 = tparams.map(_.typeParamId).toSet
    private def doesNotReferenceTparams(tp: ScType): Boolean = !tp.hasRecursiveTypeParameters(typeParamIds)

    private lazy val existentialArgumentSubst: ScSubstitutor = {
      ScSubstitutor.bind(tparams)(
        tp =>
          if (doesNotReferenceTparams(tp.upperType) && doesNotReferenceTparams(tp.lowerType)) {
            ScExistentialArgument(tp.name, tp.typeParameters, tp.lowerType, tp.upperType)
          } else
            ScExistentialArgument.deferred(
              tp.name,
              tp.typeParameters,
              None,
              () => existentialArgumentSubst(tp.lowerType),
              () => existentialArgumentSubst(tp.upperType)
            )
      )
    }

    def substitutor: ScSubstitutor = existentialArgumentSubst
  }



  //todo: make implementation closer to scala.tools.nsc.typechecker.Infer.Inferencer.isAsSpecific
  /**
   * @param preferGeneral whether the candidates are compared by the rules of Scala 3.7, see [[isComparedByNewRules]]
   */
  private def isAsSpecificAs(
    lhs:            InnerScalaResolveResult,
    rhs:            InnerScalaResolveResult,
    checkImplicits: Boolean,
    preferGeneral:  Boolean
  ): Boolean = {
    def isLastParameterRepeated(params: Iterable[Parameter]): Boolean =
      params.lastOption.exists(_.isRepeated)

    def isJavaMethod(m: PsiNamedElement): Boolean =
      !m.is[ScFunction, ScSyntheticFunction, ScPrimaryConstructor]

    // Scala 3 implicit candidates are compared as in `Applications.isAsGood`:
    // a value is as good as a method with explicit parameters, and is compared with any other candidate by its type
    if (isScala3ImplicitCandidate(lhs) && isScala3ImplicitCandidate(rhs)) {
      val lhsType = scala3ImplicitCandidateType(lhs)

      if (!hasExplicitParameters(lhsType)) {
        val rhsType = scala3ImplicitCandidateType(rhs)

        return hasExplicitParameters(rhsType) ||
          isAsGoodValueType(lhsType, rhsType, preferGeneral && !isOldStyleImplicit(lhs))
      }
    }

    (lhs.element, rhs.element) match {
      case (lhsElement @ (_: PsiMethod | _: ScSyntheticFunction), rhsElement @ (_: PsiMethod | _: ScSyntheticFunction)) =>
        val lhsType   = lhs.substitutor(getType(lhs))
        val lhsParams = lhs.paramsOrCandidateType(lhsType, undefine = false)
        val rhsType   = rhs.substitutor(getType(rhs))
        val rhsParams = rhs.paramsOrCandidateType(rhsType, undefine = true)

        val paramsConformance = (lhsParams, rhsParams) match {
          case (Left(p1), Left(p2)) =>
            var (params1, params2) = (p1, p2)

            if (
              (
                lhsType.is[ScTypePolymorphicType] && lhsType.is[ScTypePolymorphicType] ||
                  (isJavaMethod(lhsElement) || isJavaMethod(rhsElement))
                ) &&
              (isLastParameterRepeated(params1) ^ isLastParameterRepeated(params2))
            ) return isLastParameterRepeated(params2) //todo: this is hack!!! see SCL-3846, SCL-4048

            if (isLastParameterRepeated(params1) && !isLastParameterRepeated(params2)) params1 = params1.map {
              case p: Parameter if p.isRepeated =>
                implicit val scope: ElementScope = lhs.element.elementScope

                val newParamType = p.paramType match {
                  case ScExistentialType(q, _) => ScExistentialType(q.tryWrapIntoSeqType)
                  case paramType               => paramType.tryWrapIntoSeqType
                }

                Parameter(
                  p.name,
                  p.deprecatedName,
                  newParamType,
                  p.expectedType,
                  p.isDefault,
                  isByName = p.isByName
                )
              case p => p
            }

            val numberOfRepeatedArgsPassed =
              if (params1.nonEmpty) 0.max(argsLength - params1.length)
              else                  0

            val repeatedParamExpr =
              Expression(
                if (params1.nonEmpty) params1.last.paramType
                else                  Nothing,
                place
              )

            val argExprs =
              params1.map(p => Expression(p.paramType, place)) ++
                Seq.fill(numberOfRepeatedArgsPassed)(repeatedParamExpr)

            Compatibility.checkConformance(params2, argExprs, checkImplicits)
          case (Right(lhsType), Right(rhsType)) =>
            lhsType.conforms(rhsType, ConstraintSystem.empty) //todo: with implicits?
          case (Left(_), Right(_)) if !lhs.isImplicitCandidate => return false
          case _                                        => return true
        }

        isSatisfiableWithBounds(paramsConformance, rhsType)
      case (_, _: PsiMethod) => true
      case (_, _) =>
        val lhsType = getType(lhs)
        val rhsType = getType(rhs)
        //@TODO: similarly to the case above this should probably take implicit conversions into account
        lhsType.conforms(rhsType)
    }
  }

  /**
   * Whether `constraints` can be solved together with the bounds of the type parameters of `tpe`,
   * for which they were collected
   */
  private def isSatisfiableWithBounds(constraints: ConstraintsResult, tpe: ScType): Boolean =
    constraints match {
      case cs @ ConstraintSystem(uSubst) =>
        var u = cs
        tpe match {
          case ScTypePolymorphicType(_, typeParams) =>
            val typeParamIds = typeParams.map(_.typeParamId).toSet

            typeParams.foreach { tp =>
              val typeParamId = tp.typeParamId

              tp.lowerType match {
                case lower if lower.isNothing || lower.hasRecursiveTypeParameters(typeParamIds) =>
                case lower =>
                  u =
                    u.withLower(tp, uSubst(lower))
                      .withTypeParamId(typeParamId)
              }

              tp.upperType match {
                case upper if upper.isAny || upper.hasRecursiveTypeParameters(typeParamIds) =>
                case upper =>
                  u =
                    u.withUpper(tp, uSubst(upper))
                      .withTypeParamId(typeParamId)
              }
            }
          case _ =>
        }

        ConstraintSystem.unapply(u).isDefined
      case _ => false
    }

  /**
   * Whether the value type `lhsType` of an implicit candidate is as good as `rhsType` (`Applications.isAsGoodValueType`):
   * a subtype of it, or a supertype of it if `preferGeneral`.
   * The type parameters of `lhsType` are fixed, while those of `rhsType` are inferred.
   * For example, `Ord[Int]` is as good as `[T] Ord[T]` with and without `preferGeneral`,
   * while `[T] Ord[T]` is never as good as `Ord[Int]`.
   */
  private def isAsGoodValueType(lhsType: ScType, rhsType: ScType, preferGeneral: Boolean): Boolean = {
    val lhsValueType = lhsType match {
      case ScTypePolymorphicType(internal, _) => internal
      case tpe                                => tpe
    }

    val rhsValueType = rhsType match {
      case ScTypePolymorphicType(internal, typeParams) => ScSubstitutor.bind(typeParams)(UndefinedType(_))(internal)
      case tpe                                         => tpe
    }

    val conformance =
      if (preferGeneral) rhsValueType.conforms(lhsValueType, ConstraintSystem.empty)
      else               lhsValueType.conforms(rhsValueType, ConstraintSystem.empty)

    isSatisfiableWithBounds(conformance, rhsType)
  }

  private def hasExplicitParameters(tpe: ScType): Boolean = tpe match {
    case ScMethodType(_, _, isImplicit)     => !isImplicit
    case ScTypePolymorphicType(internal, _) => hasExplicitParameters(internal)
    case _                                  => false
  }

  /**
   * Whether `cand` is a candidate of an implicit search in a Scala 3 file,
   * rather than an extension method or a member of the result of an implicit conversion
   */
  private def isScala3ImplicitCandidate(cand: InnerScalaResolveResult): Boolean =
    cand.isImplicitCandidate &&
      !cand.repr.isExtensionCall &&
      cand.repr.implicitConversion.isEmpty &&
      place.isInScala3File

  /**
   * The type by which a Scala 3 implicit candidate is compared (`Applications.compare`):
   *  - without its implicit and `using` clauses (`stripImplicit`)
   *  - a structural given by its parents (`prepare` and `widenGiven`). For example, `given ord[T]: Ord[List[T]] with {}`
   *    is represented by a class `ord[T]` and a method of type `[T] ord[T]`, which is compared as `[T] Ord[List[T]]`.
   */
  private def scala3ImplicitCandidateType(cand: InnerScalaResolveResult): ScType = {
    def withoutImplicitClauses(tpe: ScType): ScType = tpe match {
      case ScMethodType(result, _, true)               => withoutImplicitClauses(result)
      case ScTypePolymorphicType(internal, typeParams) => ScTypePolymorphicType(withoutImplicitClauses(internal), typeParams)
      case _                                           => tpe
    }

    def widenToParents(tpe: ScType, givenDefinition: ScGivenDefinition): ScType = tpe match {
      case ScTypePolymorphicType(internal, typeParams) =>
        ScTypePolymorphicType(widenToParents(internal, givenDefinition), typeParams)
      case _ =>
        tpe.extractClassType match {
          case Some((cls: ScTypeDefinition, subst)) if ScGiven.Original.unapply(cls).contains(givenDefinition) =>
            cls.superTypes.map(subst) match {
              case Seq(parent) => parent
              case parents     => ScCompoundType(parents)
            }
          case _ => tpe
        }
    }

    val tpe = withoutImplicitClauses(cand.substitutor(getType(cand)))

    cand.element match {
      case ScGivenDefinition.DesugaredDefinition(givenDefinition) => widenToParents(tpe, givenDefinition)
      case _                                                      => tpe
    }
  }

  /**
   * Whether an implicit candidate is an old-style implicit definition or parameter (`Implicit` in the compiler),
   * rather than a given, a context parameter or a given pattern
   */
  private def isOldStyleImplicit(cand: InnerScalaResolveResult): Boolean = cand.element match {
    case _: ScGiven | _: ScGivenPattern | _: ScContextBound     => false
    case ScGivenDefinition.DesugaredDefinition(_)               => false
    case parameter: ScParameter                                 => parameter.isInClauseWithImplicit
    case owner: ScModifierListOwner                             => owner.hasModifierProperty("implicit")
    case ScalaPsiUtil.inNameContext(owner: ScModifierListOwner) => owner.hasModifierProperty("implicit")
    case _                                                      => false
  }

  /**
   * Whether two implicit candidates are compared by the rules of source version 3.7 (`CompareScheme.New` in the compiler):
   * a more general candidate is preferred to a more specific one, and the owner decides if one owner derives
   * from the other. These rules don't apply when both candidates are old-style implicits, see [[isOldStyleImplicit]].
   */
  private def isComparedByNewRules(r1: InnerScalaResolveResult, r2: InnerScalaResolveResult): Boolean =
    isScala3ImplicitCandidate(r1) &&
      isScala3ImplicitCandidate(r2) &&
      newRulesApply &&
      !(isOldStyleImplicit(r1) && isOldStyleImplicit(r2))

  private lazy val newRulesApply: Boolean =
    place.effectiveLanguageLevel >= ScalaLanguageLevel.Scala_3_7

  private def getContainingClass(element: PsiNamedElement): Option[PsiClass] =
    for {
      member <- element.nameContext.asOptionOf[PsiMember]
      cls    <- member.containingClass.toOption
    } yield cls

  private def extractContainingClass(srr: InnerScalaResolveResult): Option[PsiClass] = getContainingClass(srr.element)

  /**
   * 1) `c1` is a subclass of `c2`, or
   * 2) `c1` is a companion object of a class derived from `c2`, or
   * 3) `c2` is a companion object of a class from which `c1` is derived.
   */
  private def isDerived(cls: PsiClass, base: PsiClass): Boolean = {
    if (cls == base)                                  false
    else if (ScalaPsiUtil.isInheritorDeep(cls, base)) true
    else
      (cls, base) match {
        case (obj: ScObject, _) =>
          val companionClass = ScalaPsiUtil.getCompanionModule(obj)
          companionClass.exists(isDerived(_, base))
        case (_, baseObj: ScObject) =>
          val baseObjectCompanionClass = ScalaPsiUtil.getCompanionModule(baseObj)
          baseObjectCompanionClass.exists(isDerived(cls, _))
        case _ => false
      }
  }

  def isInMoreSpecificClass(r1: ScalaResolveResult, r2: ScalaResolveResult): Boolean =
    (getContainingClass(r1.element), getContainingClass(r2.element)) match {
      case (Some(clazz1), Some(clazz2)) =>
        clazz1.qualifiedName != clazz2.qualifiedName &&
          ScalaPsiUtil.isInheritorDeep(clazz1, clazz2) &&
          !ScalaPsiUtil.isInheritorDeep(clazz2, clazz1)
      case _ => false
    }

  /**
   * @param preferGeneral whether the candidates are compared by the rules of Scala 3.7, see [[isComparedByNewRules]].
   *                      Then the owner decides if one owner derives from the other (`drawOrOwner` in `Applications.compare`).
   */
  private def relativeWeight(
    r1:             InnerScalaResolveResult,
    r2:             InnerScalaResolveResult,
    checkImplicits: Boolean,
    preferGeneral:  Boolean
  ): Int = {
    val asSpecific = if (isAsSpecificAs(r1, r2, checkImplicits, preferGeneral)) 1 else 0

    val derivedWeight = if (preferGeneral) 2 else 1

    val derived =
      (
        for {
          cls1 <- extractContainingClass(r1)
          cls2 <- extractContainingClass(r2)
        } yield
          if (isDerived(cls1, cls2)) derivedWeight
          else                       0
        ).getOrElse(0)

    asSpecific + derived
  }

  private def isMoreSpecific(
    r1:             InnerScalaResolveResult,
    r2:             InnerScalaResolveResult,
    checkImplicits: Boolean
  ): Boolean = {
    def hasImplicitParameters(isrr: InnerScalaResolveResult): Boolean =
      isrr.element match {
        case fn: ScFunction =>
          //@TODO: parameterClausesWithExtension.flatMap(_.effectiveParameters)?
          fn.parameters.exists(_.isImplicit)
        case _              => false
      }

    ProgressManager.checkCanceled()

    (r1.implicitConversionClass, r2.implicitConversionClass) match {
      case (Some(t1), Some(t2)) if ScalaPsiUtil.isInheritorDeep(t1, t2) => true
      case _ =>
        if (r1.repr.isExtensionCall ^ r2.repr.isExtensionCall)
          false
        else if (r1.callByNameImplicit ^ r2.callByNameImplicit)
          !r1.callByNameImplicit
        else {
          val preferGeneral = isComparedByNewRules(r1, r2)
          val weightR1R2    = relativeWeight(r1, r2, checkImplicits, preferGeneral)
          val weightR2R1    = relativeWeight(r2, r1, checkImplicits, preferGeneral)

          /**
           * Scala 3 expands of the notion of specificity (SLS §6.26.3) in the following ways:
           * 1. If the relative weights are the same, and `r1` takes implicit/context
           *    parameters, while `r2` does not, `r1` is considered more specific.
           * 2. If the relative weights are the same, and both `r1` and  `r2` take implicit/context
           *    parameters, relative weights are recomputed and compared as if `r1` and `r2`
           *    take normal (non-implicit) parameters.
           */
          if (place.isInScala3File && weightR1R2 == weightR2R1 && r1.isImplicitCandidate) {
            val r1HasImplicitParameters = hasImplicitParameters(r1)
            val r2HasImplicitParameters = hasImplicitParameters(r2)

            if (!r1HasImplicitParameters && r2HasImplicitParameters)
              true
            else if (r1HasImplicitParameters && r2HasImplicitParameters) {
              val r1withParams = r1.copy(isImplicitCandidate = false)
              val r2withParams = r2.copy(isImplicitCandidate = false)

              val weightWithParamsR1R2 = relativeWeight(r1withParams, r2withParams, checkImplicits, preferGeneral)
              val weightWithParamsR2R1 = relativeWeight(r2withParams, r1withParams, checkImplicits, preferGeneral)

              weightWithParamsR1R2 > weightWithParamsR2R1
            } else false
          } else weightR1R2 > weightR2R1
        }
    }
  }

  private def mostSpecificGeneric(
    applicable: Iterable[InnerScalaResolveResult],
    noImplicit: Boolean
  ): Option[InnerScalaResolveResult] = {
    def calc(checkImplicits: Boolean): Option[InnerScalaResolveResult] = {
      val a1iterator = applicable.iterator

      while (a1iterator.hasNext) {
        val a1 = a1iterator.next()
        var break = false
        val a2iterator = applicable.iterator
        while (a2iterator.hasNext && !break) {
          val a2 = a2iterator.next()
          if (a1 != a2 && !isMoreSpecific(a1, a2, checkImplicits)) break = true
        }
        if (!break) return Option(a1)
      }

      None
    }

    val result = calc(checkImplicits = false)

    if (!noImplicit && result.isEmpty)
      calc(checkImplicits = true)
    else
      result
  }

  private def nextMostSpecificGeneric(rest: Iterable[InnerScalaResolveResult]): Option[InnerScalaResolveResult] =
    if (rest.isEmpty)        None
    else if (rest.size == 1) Option(rest.head)
    else  {
      val iter     = rest.iterator
      var foundMax = iter.next()

      while (iter.hasNext) {
        val res = iter.next()

        for {
          cls1 <- extractContainingClass(res)
          cls2 <- extractContainingClass(foundMax)
        } if (isDerived(cls1, cls2)) foundMax = res
      }

      Option(foundMax)
    }

  private def getType(cand: InnerScalaResolveResult): ScType = {
    import cand.{element, isImplicitCandidate}

    val isExtensionCall = cand.repr.isExtensionCall

    val unresolvedExtensionTypeParams =
      if (isExtensionCall) cand.repr.unresolvedTypeParameters.getOrElse(Seq.empty)
      else                 Seq.empty

    val res = element match {
      case m: PsiMethod =>
        val scope = place.elementScope
        m.methodTypeProvider(scope).polymorphicType()
      case fun: ScSyntheticFunction => fun.polymorphicType()
      case refPatt: ScReferencePattern => refPatt.getParent /*id list*/ .getParent match {
        case pd: ScPatternDefinition if PsiTreeUtil.isContextAncestor(pd, place, true) =>
          pd.declaredType.getOrElse(Nothing)
        case vd: ScVariableDefinition if PsiTreeUtil.isContextAncestor(vd, place, true) =>
          vd.declaredType.getOrElse(Nothing)
        case _ => refPatt.`type`().getOrAny
      }
      case param: ScParameter       => param.insideParamType.getOrAny
      case typed: ScTypedDefinition => typed.`type`().getOrAny
      case f: PsiField              => f.getType.toScType()
      case _                        => Nothing
    }

    val tpe = res match {
      case ScMethodType(retType, _, true) if isImplicitCandidate => retType
      case ScTypePolymorphicType(ScMethodType(retType, _, true), typeParameters) if isImplicitCandidate =>
        ScTypePolymorphicType(retType, typeParameters)
      case tp => tp
    }

    if (unresolvedExtensionTypeParams.isEmpty) tpe
    else tpe match {
      case ScTypePolymorphicType(retType, typeParams) =>
        ScTypePolymorphicType(retType, typeParams ++ unresolvedExtensionTypeParams)
      case other                                      => ScTypePolymorphicType(other, unresolvedExtensionTypeParams)
    }
  }
}

object MostSpecificUtil {
  def apply(
    place:      PsiElement,
    argsLength: Int,
  ): MostSpecificUtil = new MostSpecificUtil(place, argsLength)
}
