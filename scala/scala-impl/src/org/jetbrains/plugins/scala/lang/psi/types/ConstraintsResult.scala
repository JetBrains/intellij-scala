package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.extensions.{NonNullObjectExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamId
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.immutable.LongMap

/** ConstraintsResult allows to represent failures in conformance and equivalence
  * without wrapping [[ConstraintSystem]] into a tuple
  */
sealed trait ConstraintsResult

object ConstraintsResult {

  implicit class ConstraintsResultExt(private val result: ConstraintsResult) extends AnyVal {

    def isLeft: Boolean = result eq Left

    def isRight: Boolean = !isLeft

    def constraints: ConstraintSystem = result match {
      case constraints: ConstraintSystem => constraints
      case _                             => ConstraintSystem.empty
    }

    /**
     * The substitutor that maps every constrained type parameter to an approximation of its solution,
     * `None` if the constraints are unsolvable.
     *
     * Use [[toInstantiationSubst]] instead whenever the result becomes the actual type argument of
     * a call, since only that one widens.
     */
    def toSubst(implicit ctx: ProjectContext): Option[ScSubstitutor] = result match {
      case ConstraintSystem(subst) => Some(subst)
      case _                       => None
    }

    /** Like [[toSubst]], but falls back to the identity substitutor instead of `None`. */
    def substOrEmpty(implicit ctx: ProjectContext): ScSubstitutor =
      toSubst.getOrElse(ScSubstitutor.empty)

    /**
     * Like [[toSubst]], but for callers that ''instantiate'' the type parameters, so that type
     * arguments inferred from below are widened, see [[ConstraintSystem.substitutionBounds]].
     *
     * @example {{{
     *   def f[T](x: T): T = x
     *   f(1) // toSubst: T -> 1, toInstantiationSubst: T -> Int
     * }}}
     */
    def toInstantiationSubst(implicit ctx: ProjectContext, context: Context): Option[ScSubstitutor] =
      result match {
        case constraints: ConstraintSystem =>
          constraints
            .substitutionBounds(canThrowSCE = true, widenInferredTypeArguments = true)
            .map(_.substitutor)
        case _ => None
      }

    /** Like [[toInstantiationSubst]], but falls back to the identity substitutor instead of `None`. */
    def instantiationSubstOrEmpty(implicit ctx: ProjectContext, context: Context): ScSubstitutor =
      toInstantiationSubst.getOrElse(ScSubstitutor.empty)
  }

  case object Left extends ConstraintsResult
}

/** ConstraintSystem is used to accumulate information about generic types
  * during type inference, usually in combination with [[UndefinedType]]
  */
sealed trait ConstraintSystem extends ConstraintsResult {

  def isEmpty: Boolean

  def withTypeParamId(id: Long): ConstraintSystem

  /**
   * Marks the type parameter as one whose inferred type must not be widened, because it explicitly
   * asks for a singleton type, see [[Widening.isSingletonBounded]].
   */
  def withoutWidening(id: Long): ConstraintSystem

  def withLower(id: Long, lower: ScType, variance: Variance = Contravariant)(implicit context: Context): ConstraintSystem

  def withUpper(id: Long, upper: ScType, variance: Variance = Covariant)(implicit context: Context): ConstraintSystem

  def +(constraints: ConstraintSystem)(implicit context: Context): ConstraintSystem

  def isApplicable(id: Long): Boolean

  def removeTypeParamIds(ids: Set[Long]): ConstraintSystem

  /**
   * Solves the constraints, i.e. picks a type between the lower and the upper bounds of every
   * constrained type parameter, `None` if there is no such type for one of them.
   *
   * @param canThrowSCE                whether an unsolvable constraint may be reported as `None`.
   *                                   Callers that only need a best effort approximation (to render
   *                                   a type, for instance) pass `false` and get `Nothing` for the
   *                                   type parameters that couldn't be solved.
   * @param checkWeak                  whether numeric widening is taken into account when the lower
   *                                   bounds are joined, so that the lower bounds `Int` and `Long`
   *                                   are solved as `Long` rather than as their common supertype
   * @param widenInferredTypeArguments whether a type argument that was inferred from below should be
   *                                   widened, see [[Widening.widenInferred]]. Only the callers that
   *                                   actually ''instantiate'' a type parameter should ask for this;
   *                                   callers that merely read an approximation out of the constraints
   *                                   (match type reduction, for instance) must not.
   *                                   Corresponds to the distinction between
   *                                   `ConstraintHandling.instanceType` and
   *                                   `ConstraintHandling.approximation` in the Scala 3 compiler.
   */
  def substitutionBounds(canThrowSCE: Boolean, checkWeak: Boolean = true, widenInferredTypeArguments: Boolean = false)
                        (implicit projectContext: ProjectContext, context: Context): Option[ConstraintSystem.SubstitutionBounds]
}

object ConstraintSystem {

  val empty: ConstraintSystem = ConstraintSystemImpl(
    LongMap.empty,
    LongMap.empty,
    Set.empty,
    Set.empty
  )

  //subst, lowers, uppers
  final case class SubstitutionBounds(tvMap: LongMap[ScType],
                                      lowerMap: LongMap[ScType],
                                      upperMap: LongMap[ScType]) {
    val substitutor: ScSubstitutor = ScSubstitutor(tvMap)
  }

  def apply(constraintsSet: Set[ConstraintSystem]): ConstraintSystem = {
    val flattened = constraintsSet.filterNot {
      _.isEmpty
    }.flatMap {
      case impl: ConstraintSystemImpl => Set(impl)
      case MultiConstraintSystem(impls) => impls
    }

    flattened.size match {
      case 0 => ConstraintSystem.empty
      case 1 => flattened.head
      case _ => MultiConstraintSystem(flattened)
    }
  }

  def unapply(constraints: ConstraintSystem)
             (implicit projectContext: ProjectContext, context: Context): Option[ScSubstitutor] =
    constraints.substitutionBounds(canThrowSCE = true).map {
      _.substitutor
    }
}

private final case class ConstraintSystemImpl(upperMap: LongMap[Set[ScType]],
                                              lowerMap: LongMap[Set[ScType]],
                                              additionalIds: Set[Long],
                                              noWideningIds: Set[Long])
  extends ConstraintSystem {

  import ConstraintSystem._
  import ConstraintSystemImpl._

  /**
   * Solving is expensive, so the solution of this immutable system is cached. Every flag that
   * influences the outcome has to be part of the key, or a caller would be served the solution that
   * was computed for someone else's flags.
   */
  private[this] val cachedBounds = new Array[Option[SubstitutionBounds]](1 << 3)

  private[this] def cacheIndex(canThrowSCE: Boolean, checkWeak: Boolean, widenInferredTypeArguments: Boolean): Int =
    (if (canThrowSCE) 4 else 0) | (if (checkWeak) 2 else 0) | (if (widenInferredTypeArguments) 1 else 0)

  private[this] def cachedBoundsFor(canThrowSCE: Boolean, checkWeak: Boolean, widenInferredTypeArguments: Boolean)
                                   (compute: => Option[SubstitutionBounds]): Option[SubstitutionBounds] = {
    val index = cacheIndex(canThrowSCE, checkWeak, widenInferredTypeArguments)

    cachedBounds(index) match {
      case null =>
        val value = compute
        cachedBounds(index) = value
        value
      case value => value
    }
  }

  override def isApplicable(id: Long): Boolean =
    upperMap.contains(id) || lowerMap.contains(id)

  override def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty

  override def +(constraints: ConstraintSystem)(implicit context: Context): ConstraintSystem = constraints match {
    case ConstraintSystemImpl(otherUpperMap, otherLowerMap, otherAdditionalIds, otherNoWideningIds) => ConstraintSystemImpl(
      upperMap.merge(otherUpperMap)(isAny),
      lowerMap.merge(otherLowerMap)(isNothing),
      additionalIds ++ otherAdditionalIds,
      noWideningIds ++ otherNoWideningIds
    )
    case multi: MultiConstraintSystem => multi + this
  }

  override def withTypeParamId(id: Long): ConstraintSystem = copy(
    additionalIds = additionalIds + id
  )

  override def withoutWidening(id: Long): ConstraintSystem = copy(
    noWideningIds = noWideningIds + id
  )

  override def withLower(id: Long, rawLower: ScType, variance: Variance)(implicit context: Context): ConstraintSystem =
    computeLower(variance, rawLower) match {
      case None => this
      case Some(lower) => copy(lowerMap = lowerMap.update(id, lower))
    }

  override def withUpper(id: Long, rawUpper: ScType, variance: Variance)(implicit context: Context): ConstraintSystem =
    computeUpper(variance, rawUpper) match {
      case None => this
      case Some(upper) => copy(upperMap = upperMap.update(id, upper))
    }

  override def substitutionBounds(canThrowSCE: Boolean, checkWeak: Boolean, widenInferredTypeArguments: Boolean)
                                 (implicit projectContext: ProjectContext, context: Context): Option[SubstitutionBounds] =
    cachedBoundsFor(canThrowSCE, checkWeak, widenInferredTypeArguments) {
      substitutionBoundsImpl(canThrowSCE, checkWeak, widenInferredTypeArguments)
    }

  override def removeTypeParamIds(ids: Set[Long]): ConstraintSystem = copy(
    upperMap = upperMap.removeIds(ids),
    lowerMap = lowerMap.removeIds(ids),
    noWideningIds = noWideningIds -- ids
  )

  private def substitutionBoundsImpl(canThrowSCE: Boolean, checkWeak: Boolean, widenInferredTypeArguments: Boolean)
                                    (implicit projectContext: ProjectContext, context: Context): Option[SubstitutionBounds] = {
    var tvMap = LongMap.empty[ScType]
    var lMap = LongMap.empty[ScType]
    var uMap = LongMap.empty[ScType]

    def solve(visited: Set[Long])
             (id: Long): Boolean = {
      if (visited.contains(id)) {
        tvMap += ((id, Nothing))
        return false
      }

      tvMap.contains(id) || {
        val needTvMap = {
          val newVisited = visited + id
          recursion(!solve(newVisited)(_) && canThrowSCE) _
        }

        val instantiatedFromBelow =
          lowerMap.getOrDefault(id) match {
            case set if set.nonEmpty =>
              val substitutor = needTvMap(set).fold {
                tvMap += ((id, Nothing))
                return false
              } {
                case true => ScSubstitutor(tvMap)
                case _    => ScSubstitutor.empty
              }

              val lower = set.map(substitutor).reduce(_.lub(_, checkWeak))

              lMap  += ((id, lower))
              tvMap += ((id, lower))
              true
            case _ =>
              false
          }

        upperMap.getOrDefault(id) match {
          case set if set.nonEmpty =>
            val substitutor = needTvMap(set).fold {
              tvMap += ((id, Nothing))
              return false
            } {
              case true => ScSubstitutor(tvMap)
              case _ => ScSubstitutor.empty
            }

            val upper = set.map(substitutor).reduce(_.glb(_, checkWeak))
            uMap += ((id, upper))

            tvMap.get(id) match {
              case Some(lower) =>
                if (canThrowSCE && !lower.conforms(upper)) {
                  return false
                }
              case _ => tvMap += ((id, upper))
            }
          case _ =>
        }

        // A type argument that was inferred from below is widened, so that e.g. `Some(1)` is a
        // `Some[Int]` and not a `Some[1]`.
        // Corresponds to `ConstraintHandling.instanceType` in the Scala 3 compiler.
        if (widenInferredTypeArguments && instantiatedFromBelow && !noWideningIds(id)) {
          tvMap.get(id).foreach { inferred =>
            tvMap += ((id, Widening.widenInferred(inferred, uMap.get(id))))
          }
        }

        if (!tvMap.contains(id)) {
          tvMap += ((id, Nothing))
        }
        tvMap.contains(id)
      }
    }

    for ((id, _) <- upperMap.iterator ++ lowerMap.iterator) {
      if (!solve(Set.empty)(id) && canThrowSCE) return None
    }

    Some(SubstitutionBounds(tvMap, lMap, uMap))
  }

  private def recursion(break: Long => Boolean)
                       (set: Set[ScType]): Option[Boolean] = {
    def predicate(flag: Ref[Boolean])
                 (`type`: ScType): Boolean = {
      def innerBreak[T](owner: T)
                       (visited: Long => Boolean)
                       (implicit evidence: TypeParamId[T]) = evidence.typeParamId(owner) match {
        case id if visited(id) =>
          flag.set(true)
          break(id)
        case _ => false
      }

      `type`.visitRecursively {
        case tpt: TypeParameterType if innerBreak(tpt)(additionalIds.contains) => return false
        case UndefinedType(tp, _) if innerBreak(tp)(isApplicable) => return false
        case _ =>
      }

      true
    }

    Ref.create(false) match {
      case needTvMap if set.forall(predicate(needTvMap)) => Some(needTvMap.get)
      case _ => None
    }
  }
}

private object ConstraintSystemImpl {

  private implicit class LongMapExt(private val map: LongMap[Set[ScType]]) extends AnyVal {

    def getOrDefault(id: Long): Set[ScType] = map.getOrElse(id, Set.empty)

    def removeIds(set: Set[Long]): LongMap[Set[ScType]] = map.filterNot {
      case (long, _) => set(long)
    }

    def update(id: Long, `type`: ScType): LongMap[Set[ScType]] =
      map.updated(id, getOrDefault(id) + `type`)

    def merge(map: LongMap[Set[ScType]])
             (predicate: ScType => Boolean): LongMap[Set[ScType]] = {
      this.map.unionWith(map, (_, left, right) => left ++ right).modifyOrRemove { (_, set) =>
        set.filterNot(predicate) match {
          case filtered if filtered.nonEmpty => Some(filtered)
          case _ => None
        }
      }
    }
  }

  private def computeUpper(variance: Variance, rawUpper: ScType)(implicit context: Context): Option[ScType]  =
    updateUpper(variance, rawUpper)
      .unpackedType
      .ifNot(isAny)

  private def computeLower(variance: Variance, rawLower: ScType)(implicit context: Context): Option[ScType] =
    updateLower(variance, rawLower)
      .unpackedType
      .ifNot(isNothing)

  private def isAny(`type`: ScType)(implicit context: Context) = {
    import `type`.projectContext
    `type`.equiv(Any)
  }

  private def isNothing(`type`: ScType)(implicit context: Context) = {
    import `type`.projectContext
    `type`.equiv(Nothing)
  }

  private[this] def updateUpper(variance: Variance, rawUpper: ScType)
                               (implicit freshExArg: FreshExistentialArg): ScType =
    rawUpper match {
      case UndefinedType(tp, _)                                 => TypeParameterType(tp)
      case ScAbstractType(_, _, upper) if variance == Invariant => upper
      case ScAbstractType(_, _, upper) if variance == Covariant && isAny(upper) =>
        import upper.projectContext
        Any
      case _ =>
        recursiveVarianceUpdate(rawUpper, variance)(
          invariantAbstract = freshExArg(_),                                       // TODO: why this is right?
          invariantExistentialArg = freshExArg(_)
        )
    }

  private[this] def updateLower(variance: Variance, rawLower: ScType)
                               (implicit freshExArg: FreshExistentialArg): ScType =
    rawLower match {
      case UndefinedType(tp, _)        => TypeParameterType(tp)
      case ScAbstractType(_, lower, _) => lower
      case ex: ScExistentialArgument if variance.isInvariant => freshExArg(ex)
      case _ =>
        recursiveVarianceUpdate(rawLower, -variance.sign)(
          invariantAbstract = _.lower,                                             // TODO: why this is right?
          invariantExistentialArg = freshExArg(_)
        )
    }

  private[this] def recursiveVarianceUpdate(`type`: ScType, variance: Variance)
                                           (invariantAbstract: ScAbstractType => ScType,
                                            invariantExistentialArg: ScExistentialArgument => ScType) =
    `type`.recursiveVarianceUpdate(variance) {
        case (a: ScAbstractType, newVariance)         => replaceAbstractType(newVariance, a)(invariantAbstract)
        case (ex: ScExistentialArgument, newVariance) => replaceExistentialArg(newVariance, ex)(invariantExistentialArg)
        case (_: ScExistentialType, _)                => Stop
        case _                                        => ProcessSubtypes
    }

  private[this] def replaceAbstractType(variance: Variance, a: ScAbstractType)
                                       (invariantCase: ScAbstractType => ScType) = ReplaceWith {
    variance match {
      case Contravariant => a.lower
      case Covariant => a.upper
      case Invariant => invariantCase(a)
    }
  }

  private[this] def replaceExistentialArg(variance: Variance, ex: ScExistentialArgument)
                                         (invariantCase: ScExistentialArgument => ScType) = ReplaceWith {
    variance match {
      case Contravariant => ex.lower
      case Covariant => ex.upper
      case Invariant => invariantCase(ex)
    }
  }

  private implicit def freshExistentialArg: FreshExistentialArg = new FreshExistentialArg

  private class FreshExistentialArg {
    private[this] var index = 0

    def apply(a: ScAbstractType): ScExistentialArgument = {
      index += 1
      ScExistentialArgument(s"_$$$index", Nil, a.lower, a.upper)
    }

    def apply(e: ScExistentialArgument): ScExistentialArgument = {
      index += 1
      ScExistentialArgument(s"_$$$index", Nil, e.lower, e.upper)
    }
  }
}

private final case class MultiConstraintSystem(impls: Set[ConstraintSystemImpl])
  extends ConstraintSystem {

  override def isApplicable(id: Long): Boolean = impls.exists {
    _.isApplicable(id)
  }

  override def isEmpty: Boolean = impls.forall {
    _.isEmpty
  }

  override def withTypeParamId(id: Long): ConstraintSystem = map {
    _.withTypeParamId(id)
  }

  override def withoutWidening(id: Long): ConstraintSystem = map {
    _.withoutWidening(id)
  }

  override def withLower(id: Long, lower: ScType, variance: Variance)(implicit context: Context): ConstraintSystem = map {
    _.withLower(id, lower, variance)
  }

  override def withUpper(id: Long, upper: ScType, variance: Variance)(implicit context: Context): ConstraintSystem = map {
    _.withUpper(id, upper, variance)
  }

  override def substitutionBounds(canThrowSCE: Boolean, checkWeak: Boolean, widenInferredTypeArguments: Boolean)
                                 (implicit projectContext: ProjectContext, context: Context): Option[ConstraintSystem.SubstitutionBounds] =
    impls.iterator.flatMap {
      _.substitutionBounds(canThrowSCE, checkWeak, widenInferredTypeArguments)
    }.nextOption()

  override def removeTypeParamIds(ids: Set[Long]): ConstraintSystem = map {
    _.removeTypeParamIds(ids)
  }

  override def +(constraints: ConstraintSystem)(implicit context: Context): ConstraintSystem = {
    val otherImpls = constraints match {
      case impl: ConstraintSystemImpl => Set(impl)
      case MultiConstraintSystem(otherSubstitutors) => otherSubstitutors
    }

    ConstraintSystem {
      for {
        left <- impls
        right <- otherImpls.headOption

//        todo: cartesian product of all constraints may lead to exponential blow up and OutOfMemoryError
//         is it even necessary?
//        right <- otherImpls
      } yield left + right
    }
  }

  private def map(function: ConstraintSystemImpl => ConstraintSystem) =
    ConstraintSystem(impls.map(function))
}