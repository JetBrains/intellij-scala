package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.extensions.{ObjectExt, IteratorExt}
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
    computeLower(variance, rawLower) match {
      case None => this
      case Some(lower) => copy(lowerMap = lowerMap.update(id, lower))
    }

  override def withUpper(id: Long, rawUpper: ScType, variance: Variance): ConstraintSystem =
    computeUpper(variance, rawUpper) match {
      case None => this
      case Some(upper) => copy(upperMap = upperMap.update(id, upper))
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

  private def computeUpper(variance: Variance, rawUpper: ScType): Option[ScType]  =
    updateUpper(variance, rawUpper)
      .unpackedType
      .ifNot(isAny)

  private def computeLower(variance: Variance, rawLower: ScType): Option[ScType] =
    updateLower(variance, rawLower)
      .unpackedType
      .ifNot(isNothing)

  private def isAny(`type`: ScType) = {
    import `type`.projectContext
    `type`.equiv(Any)
  }

  private def isNothing(`type`: ScType) = {
    import `type`.projectContext
    `type`.equiv(Nothing)
  }

  private[this] def updateUpper(variance: Variance, rawUpper: ScType)
                               (implicit freshExArg: FreshExistentialArg): ScType =
    rawUpper match {
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

  override def withLower(id: Long, lower: ScType, variance: Variance): ConstraintSystem = map {
    _.withLower(id, lower, variance)
  }

  override def withUpper(id: Long, upper: ScType, variance: Variance): ConstraintSystem = map {
    _.withUpper(id, upper, variance)
  }

  override def substitutionBounds(canThrowSCE: Boolean)
                                 (implicit context: ProjectContext): Option[ConstraintSystem.SubstitutionBounds] =
    impls.iterator.flatMap {
      _.substitutionBounds(canThrowSCE)
    }.headOption

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