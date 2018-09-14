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

sealed trait ConstraintsResult {
  def isFailure: Boolean

  final def isSuccess: Boolean = !isFailure

  def substitutor: ScUndefinedSubstitutor

  def combine(other: ConstraintsResult): ConstraintsResult
}

object ConstraintsResult {
  def unapply(cr: ConstraintsResult): Option[ScUndefinedSubstitutor] = cr match {
    case Failure => None
    case substitutor: ScUndefinedSubstitutor => Some(substitutor)
  }

  case object Failure extends ConstraintsResult {
    override def isFailure: Boolean = true

    override def substitutor: ScUndefinedSubstitutor = ScUndefinedSubstitutor()

    override def combine(other: ConstraintsResult): ConstraintsResult = this
  }
}

sealed trait ScUndefinedSubstitutor extends ConstraintsResult {

  override def isFailure: Boolean = false

  override def substitutor: ScUndefinedSubstitutor = this

  override def combine(other: ConstraintsResult): ConstraintsResult = other match {
    case ConstraintsResult.Failure => ConstraintsResult.Failure
    case substitutor: ScUndefinedSubstitutor =>
      if (substitutor.isEmpty) this
      else this + substitutor
  }

  def isEmpty: Boolean

  def addLower(id: Long, lower: ScType,
               additional: Boolean = false, variance: Variance = Contravariant): ScUndefinedSubstitutor

  def addUpper(id: Long, upper: ScType,
               additional: Boolean = false, variance: Variance = Covariant): ScUndefinedSubstitutor

  def +(substitutor: ScUndefinedSubstitutor): ScUndefinedSubstitutor

  def typeParamIds: Set[Long]

  def removeTypeParamIds(ids: Set[Long]): ScUndefinedSubstitutor

  def substitutionBounds(canThrowSCE: Boolean)
                        (implicit context: ProjectContext): Option[ScUndefinedSubstitutor.SubstitutionBounds]
}

object ScUndefinedSubstitutor {

  //subst, lowers, uppers
  final case class SubstitutionBounds(tvMap: LongMap[ScType],
                                      lowerMap: LongMap[ScType],
                                      upperMap: LongMap[ScType]) {
    val substitutor = ScSubstitutor(tvMap)
  }

  def apply(): ScUndefinedSubstitutor = empty

  def apply(substitutors: Set[ScUndefinedSubstitutor]): ScUndefinedSubstitutor = {
    val newSubstitutors = substitutors.filterNot {
      _.isEmpty
    }.flatMap {
      case impl: ScUndefinedSubstitutorImpl => Set(impl)
      case ScMultiUndefinedSubstitutor(impls) => impls
    }

    newSubstitutors.size match {
      case 0 => ScUndefinedSubstitutor()
      case 1 => newSubstitutors.head
      case _ => ScMultiUndefinedSubstitutor(newSubstitutors)
    }
  }

  def unapply(substitutor: ScUndefinedSubstitutor)
             (implicit context: ProjectContext): Option[ScSubstitutor] =
    substitutor.substitutionBounds(canThrowSCE = true).map {
      _.substitutor
    }

  private val empty = ScUndefinedSubstitutorImpl(
    LongMap.empty,
    LongMap.empty,
    Set.empty
  )
}

private final case class ScUndefinedSubstitutorImpl(upperMap: LongMap[Set[ScType]],
                                                    lowerMap: LongMap[Set[ScType]],
                                                    additionalIds: Set[Long])
  extends ScUndefinedSubstitutor {

  import ScUndefinedSubstitutor._
  import ScUndefinedSubstitutorImpl._

  private[this] var substWithBounds: Option[SubstitutionBounds] = _

  private[this] var substWithBoundsNoSCE: Option[SubstitutionBounds] = _

  lazy val typeParamIds: Set[Long] = upperMap.keySet ++ lowerMap.keySet

  override def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty

  override def +(substitutor: ScUndefinedSubstitutor): ScUndefinedSubstitutor = substitutor match {
    case ScUndefinedSubstitutorImpl(otherUpperMap, otherLowerMap, otherAdditionalIds) => ScUndefinedSubstitutorImpl(
      upperMap.merge(otherUpperMap)(isAny),
      lowerMap.merge(otherLowerMap)(isNothing),
      additionalIds ++ otherAdditionalIds
    )
    case multi: ScMultiUndefinedSubstitutor => multi + this
  }

  override def addLower(id: Long, rawLower: ScType,
                        additional: Boolean, variance: Variance): ScUndefinedSubstitutor =
    computeLower(variance)(rawLower).fold(this: ScUndefinedSubstitutor) { lower =>
      copy(
        lowerMap = lowerMap.update(id, lower),
        additionalIds = additionalIds ++ id.toIterable(additional)
      )
    }

  override def addUpper(id: Long, rawUpper: ScType,
                        additional: Boolean, variance: Variance): ScUndefinedSubstitutor =
    computeUpper(variance)(rawUpper).fold(this: ScUndefinedSubstitutor) { upper =>
      copy(
        upperMap = upperMap.update(id, upper),
        additionalIds = additionalIds ++ id.toIterable(additional)
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

  override def removeTypeParamIds(ids: Set[Long]): ScUndefinedSubstitutor = copy(
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

            val lower = set.map(substitutor.subst).reduce {
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

            val upper = set.map(substitutor.subst).reduce {
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

    for (id <- typeParamIds) {
      if (!solve(Set.empty)(id) && canThrowSCE) return None
    }

    Some(SubstitutionBounds(tvMap, lMap, uMap))
  }

  private def recursion(break: Long => Boolean)
                       (set: Set[ScType]): Option[Boolean] = {
    def predicate(flag: Ref[Boolean])
                 (`type`: ScType): Boolean = {
      def innerBreak[T](set: Set[Long], owner: T)
                       (implicit evidence: TypeParamId[T]) = evidence.typeParamId(owner) match {
        case id if set(id) =>
          flag.set(true)
          break(id)
        case _ => false
      }

      `type`.visitRecursively {
        case tpt: TypeParameterType if innerBreak(additionalIds, tpt) => return false
        case UndefinedType(tp, _) if innerBreak(typeParamIds, tp) => return false
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

private object ScUndefinedSubstitutorImpl {

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

  private implicit class LongExt(val id: Long) extends AnyVal {

    def toIterable(flag: Boolean): Option[Long] =
      if (flag) Some(id) else None
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
          index += 1
          existentialArgument(index, _, _)
          // TODO: why this is right?
        }, {
          index += 1
          existentialArgument(index, _, _)
        }
      )
  }

  private[this] def updateLower(variance: Variance): ScType => ScType = {
    case ScAbstractType(_, lower, _) => lower // upper will be added separately
    case rawLower =>
      var index = 0
      recursiveVarianceUpdate(rawLower, variance, revertVariances = true)(
        {
          case (lower, _) => lower
          //          index += 1
          //          existentialArgument(index, Nil, _, _)
          // TODO: why this is right?
        }, {
          index += 1
          existentialArgument(index, _, _)
        }
      )
  }

  private[this] def recursiveVarianceUpdate(`type`: ScType, variance: Variance, revertVariances: Boolean = false)
                                           (abstractCase: (ScType, ScType) => ScType,
                                            existentialArgumentCase: (ScType, ScType) => ScType) =
    `type`.recursiveVarianceUpdate(
      {
        case (ScAbstractType(_, lower, upper), newVariance) => replaceWith(newVariance, lower, upper)(abstractCase)
        case (ScExistentialArgument(_, _, lower, upper), newVariance) => replaceWith(newVariance, lower, upper)(existentialArgumentCase)
        case (_: ScExistentialType, _) => Stop
        case _ => ProcessSubtypes
      },
      variance,
      revertVariances = revertVariances
    )

  private[this] def replaceWith(variance: Variance, lower: ScType, upper: ScType)
                               (invariantCase: (ScType, ScType) => ScType) = ReplaceWith {
    variance match {
      case Contravariant => lower
      case Covariant => upper
      case Invariant => invariantCase(lower, upper)
    }
  }

  private[this] def existentialArgument(index: Int, lower: ScType, upper: ScType) =
    ScExistentialArgument(s"_$$$index", Nil, lower, upper)

  private[this] def unpackedType(predicate: ScType => Boolean)
                                (`type`: ScType) =
    Some(`type`.unpackedType).filterNot(predicate)
}

private final case class ScMultiUndefinedSubstitutor(impls: Set[ScUndefinedSubstitutorImpl])
  extends ScUndefinedSubstitutor {

  override val typeParamIds: Set[Long] = impls.flatMap(_.typeParamIds)

  override def isEmpty: Boolean = typeParamIds.isEmpty

  override def addLower(id: Long, lower: ScType,
                        additional: Boolean, variance: Variance): ScUndefinedSubstitutor = this {
    _.addLower(id, lower, additional, variance)
  }

  override def addUpper(id: Long, upper: ScType,
                        additional: Boolean, variance: Variance): ScUndefinedSubstitutor = this {
    _.addUpper(id, upper, additional, variance)
  }

  override def substitutionBounds(canThrowSCE: Boolean)
                                 (implicit context: ProjectContext): Option[ScUndefinedSubstitutor.SubstitutionBounds] =
    impls.iterator.map {
      _.substitutionBounds(canThrowSCE)
    }.collectFirst {
      case Some(bounds) => bounds
    }

  override def removeTypeParamIds(ids: Set[Long]): ScUndefinedSubstitutor = this {
    _.removeTypeParamIds(ids)
  }

  override def +(substitutor: ScUndefinedSubstitutor): ScUndefinedSubstitutor = {
    val otherImpls = substitutor match {
      case impl: ScUndefinedSubstitutorImpl => Set(impl)
      case ScMultiUndefinedSubstitutor(otherSubstitutors) => otherSubstitutors
    }

    ScUndefinedSubstitutor {
      for {
        left <- impls
        right <- otherImpls
      } yield left + right
    }
  }

  private def apply(function: ScUndefinedSubstitutorImpl => ScUndefinedSubstitutor) =
    ScUndefinedSubstitutor(impls.map(function))
}