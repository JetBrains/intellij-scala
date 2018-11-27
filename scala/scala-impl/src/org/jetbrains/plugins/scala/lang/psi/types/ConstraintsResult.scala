package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.util.Ref
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

  implicit class ConstraintsResultExt(val result: ConstraintsResult) extends AnyVal {

    def isLeft: Boolean = result eq Left

    def isRight: Boolean = !isLeft

    def constraints: ConstraintSystem = result match {
      case constraints: ConstraintSystem => constraints
      case _ => ConstraintSystem.empty
    }
  }

  case object Left extends ConstraintsResult
}

/** ConstraintSystem is used to accumulate information about generic types
  * during type inference, usually in combination with [[UndefinedType]]
  */
sealed trait ConstraintSystem extends ConstraintsResult {

  def isEmpty: Boolean

  def withTypeParamId(id: Long): ConstraintSystem

  def withLower(id: Long, lower: ScType, variance: Variance = Contravariant): ConstraintSystem

  def withUpper(id: Long, upper: ScType, variance: Variance = Covariant): ConstraintSystem

  def +(constraints: ConstraintSystem): ConstraintSystem

  def isApplicable(id: Long): Boolean

  def removeTypeParamIds(ids: Set[Long]): ConstraintSystem

  def substitutionBounds(canThrowSCE: Boolean)
                        (implicit context: ProjectContext): Option[ConstraintSystem.SubstitutionBounds]
}

object ConstraintSystem {

  val empty: ConstraintSystem = ConstraintSystemImpl(
    LongMap.empty,
    LongMap.empty,
    Set.empty
  )

  //subst, lowers, uppers
  final case class SubstitutionBounds(tvMap: LongMap[ScType],
                                      lowerMap: LongMap[ScType],
                                      upperMap: LongMap[ScType]) {
    val substitutor = ScSubstitutor(tvMap)
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
             (implicit context: ProjectContext): Option[ScSubstitutor] =
    constraints.substitutionBounds(canThrowSCE = true).map {
      _.substitutor
    }
}

private final case class ConstraintSystemImpl(upperMap: LongMap[Set[ScType]],
                                              lowerMap: LongMap[Set[ScType]],
                                              additionalIds: Set[Long])
  extends ConstraintSystem {

  import ConstraintSystem._
  import ConstraintSystemImpl._

  private[this] var substWithBounds: Option[SubstitutionBounds] = _

  private[this] var substWithBoundsNoSCE: Option[SubstitutionBounds] = _

  override def isApplicable(id: Long): Boolean =
    upperMap.contains(id) || lowerMap.contains(id)

  override def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty

  override def +(constraints: ConstraintSystem): ConstraintSystem = constraints match {
    case ConstraintSystemImpl(otherUpperMap, otherLowerMap, otherAdditionalIds) => ConstraintSystemImpl(
      upperMap.merge(otherUpperMap)(isAny),
      lowerMap.merge(otherLowerMap)(isNothing),
      additionalIds ++ otherAdditionalIds
    )
    case multi: MultiConstraintSystem => multi + this
  }

  override def withTypeParamId(id: Long): ConstraintSystem = copy(
    additionalIds = additionalIds + id
  )

  override def withLower(id: Long, rawLower: ScType, variance: Variance): ConstraintSystem =
    computeLower(variance)(rawLower).fold(this: ConstraintSystem) { lower =>
      copy(
        lowerMap = lowerMap.update(id, lower)
      )
    }

  override def withUpper(id: Long, rawUpper: ScType, variance: Variance): ConstraintSystem =
    computeUpper(variance)(rawUpper).fold(this: ConstraintSystem) { upper =>
      copy(
        upperMap = upperMap.update(id, upper)
      )
    }

  override def substitutionBounds(canThrowSCE: Boolean)
                                 (implicit context: ProjectContext): Option[SubstitutionBounds] = {
    def init(get: => Option[SubstitutionBounds])
            (set: Option[SubstitutionBounds] => Unit) = get match {
      case null =>
        val value = substitutionBoundsImpl(canThrowSCE)
        set(value)
        value
      case value => value
    }

    if (canThrowSCE) init(substWithBounds)(substWithBounds = _)
    else init(substWithBoundsNoSCE)(substWithBoundsNoSCE = _)
  }

  override def removeTypeParamIds(ids: Set[Long]): ConstraintSystem = copy(
    upperMap = upperMap.removeIds(ids),
    lowerMap = lowerMap.removeIds(ids)
  )

  private def substitutionBoundsImpl(canThrowSCE: Boolean)
                                    (implicit context: ProjectContext): Option[SubstitutionBounds] = {
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

        lowerMap.getOrDefault(id) match {
          case set if set.nonEmpty =>
            val substitutor = needTvMap(set).fold {
              tvMap += ((id, Nothing))
              return false
            } {
              case true => ScSubstitutor(tvMap)
              case _ => ScSubstitutor.empty
            }

            val lower = set.map(substitutor).reduce {
              _ lub _
            }

            lMap += ((id, lower))
            tvMap += ((id, lower))
          case _ =>
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

            val upper = set.map(substitutor).reduce {
              _ glb _
            }
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

        if (tvMap.get(id).isEmpty) {
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

  private implicit class LongMapExt(val map: LongMap[Set[ScType]]) extends AnyVal {

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

  private def computeUpper(variance: Variance) =
    updateUpper(variance).andThen {
      unpackedType(isAny)
    }

  private def computeLower(variance: Variance) =
    updateLower(variance).andThen {
      unpackedType(isNothing)
    }

  private def isAny(`type`: ScType) = {
    import `type`.projectContext
    `type`.equiv(Any)
  }

  private def isNothing(`type`: ScType) = {
    import `type`.projectContext
    `type`.equiv(Nothing)
  }

  private[this] def updateUpper(variance: Variance): ScType => ScType = {
    case ScAbstractType(_, _, upper) if variance == Invariant => upper // lower will be added separately
    case ScAbstractType(_, _, upper) if variance == Covariant && isAny(upper) =>
      import upper.projectContext
      Any
    case rawUpper =>
      var index = 0
      recursiveVarianceUpdate(rawUpper, variance)(
        {
          a =>
            index += 1
            existentialArgument(index, a.lower, a.upper)
          // TODO: why this is right?
        }, {
          ex =>
            index += 1
            existentialArgument(index, ex.lower, ex.upper)
        }
      )
  }

  private[this] def updateLower(variance: Variance): ScType => ScType = {
    case ScAbstractType(_, lower, _) => lower // upper will be added separately
    case rawLower =>
      var index = 0
      recursiveVarianceUpdate(rawLower, variance, revertVariances = true)(
        {
          a => a.lower
          //          index += 1
          //          existentialArgument(index, Nil, _, _)
          // TODO: why this is right?
        }, {
          ex =>
            index += 1
            existentialArgument(index, ex.lower, ex.upper)
        }
      )
  }

  private[this] def recursiveVarianceUpdate(`type`: ScType, variance: Variance, revertVariances: Boolean = false)
                                           (abstractCase: ScAbstractType => ScType,
                                            existentialArgumentCase: ScExistentialArgument => ScType) =
    `type`.recursiveVarianceUpdate(
      {
        case (a: ScAbstractType, newVariance)         => replaceAbstractType(newVariance, a)(abstractCase)
        case (ex: ScExistentialArgument, newVariance) => replaceExistentialArg(newVariance, ex)(existentialArgumentCase)
        case (_: ScExistentialType, _)                => Stop
        case _                                        => ProcessSubtypes
      },
      variance,
      revertVariances = revertVariances
    )

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


  private[this] def existentialArgument(index: Int, lower: ScType, upper: ScType) =
    ScExistentialArgument(s"_$$$index", Nil, lower, upper)

  private[this] def unpackedType(predicate: ScType => Boolean)
                                (`type`: ScType) =
    Some(`type`.unpackedType).filterNot(predicate)
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

  override def withLower(id: Long, lower: ScType, variance: Variance): ConstraintSystem = map {
    _.withLower(id, lower, variance)
  }

  override def withUpper(id: Long, upper: ScType, variance: Variance): ConstraintSystem = map {
    _.withUpper(id, upper, variance)
  }

  override def substitutionBounds(canThrowSCE: Boolean)
                                 (implicit context: ProjectContext): Option[ConstraintSystem.SubstitutionBounds] =
    impls.iterator.map {
      _.substitutionBounds(canThrowSCE)
    }.collectFirst {
      case Some(bounds) => bounds
    }

  override def removeTypeParamIds(ids: Set[Long]): ConstraintSystem = map {
    _.removeTypeParamIds(ids)
  }

  override def +(constraints: ConstraintSystem): ConstraintSystem = {
    val otherImpls = constraints match {
      case impl: ConstraintSystemImpl => Set(impl)
      case MultiConstraintSystem(otherSubstitutors) => otherSubstitutors
    }

    ConstraintSystem {
      for {
        left <- impls
        right <- otherImpls
      } yield left + right
    }
  }

  private def map(function: ConstraintSystemImpl => ConstraintSystem) =
    ConstraintSystem(impls.map(function))
}