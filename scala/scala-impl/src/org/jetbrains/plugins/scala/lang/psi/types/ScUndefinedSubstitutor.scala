package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.immutable.LongMap

sealed trait ScUndefinedSubstitutor {

  def isEmpty: Boolean

  def addLower(id: Long, lower: ScType,
               additional: Boolean = false, variance: Variance = Contravariant): ScUndefinedSubstitutor

  def addUpper(id: Long, upper: ScType,
               additional: Boolean = false, variance: Variance = Covariant): ScUndefinedSubstitutor

  def +(substitutor: ScUndefinedSubstitutor): ScUndefinedSubstitutor

  def typeParamIds: Set[Long]

  def removeTypeParamIds(ids: Set[Long]): ScUndefinedSubstitutor

  def substitutionBounds(nonable: Boolean): Option[ScUndefinedSubstitutor.SubstitutionBounds]

  final def getSubstitutor: Option[ScSubstitutor] = substitutionBounds(nonable = true).map {
    _.substitutor
  }
}

object ScUndefinedSubstitutor {

  //subst, lowers, uppers
  final case class SubstitutionBounds(tvMap: LongMap[ScType],
                                      lowerMap: LongMap[ScType],
                                      upperMap: LongMap[ScType]) {
    val substitutor = ScSubstitutor(tvMap)
  }

  def apply()(implicit project: ProjectContext): ScUndefinedSubstitutor = ScUndefinedSubstitutorImpl(
    LongMap.empty,
    LongMap.empty,
    Set.empty
  )

  def apply(substitutors: Set[ScUndefinedSubstitutor])
           (implicit project: ProjectContext): ScUndefinedSubstitutor = {
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

  def unapply(substitutor: ScUndefinedSubstitutor): Option[ScSubstitutor] =
    substitutor.getSubstitutor
}

private final case class ScUndefinedSubstitutorImpl(upperMap: LongMap[Set[ScType]],
                                                    lowerMap: LongMap[Set[ScType]],
                                                    additionalIds: Set[Long])
                                                   (implicit context: ProjectContext)
  extends ScUndefinedSubstitutor {

  import ScUndefinedSubstitutor._
  import ScUndefinedSubstitutorImpl._

  lazy val typeParamIds: Set[Long] = upperMap.keySet ++ lowerMap.keySet

  override def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty

  override def +(substitutor: ScUndefinedSubstitutor): ScUndefinedSubstitutor = substitutor match {
    case ScUndefinedSubstitutorImpl(otherUpperMap, otherLowerMap, otherAdditionalIds) => ScUndefinedSubstitutorImpl(
      upperMap.merge(otherUpperMap)(_.equiv(Any)),
      lowerMap.merge(otherLowerMap)(_.equiv(Nothing)),
      additionalIds ++ otherAdditionalIds
    )
    case multi: ScMultiUndefinedSubstitutor => multi + this
  }

  override def addLower(id: Long, rawLower: ScType,
                        additional: Boolean, variance: Variance): ScUndefinedSubstitutor =
    computeLower(rawLower, variance).fold(this: ScUndefinedSubstitutor) { lower =>
      copy(
        lowerMap = lowerMap.update(id, lower),
        additionalIds = additionalIds ++ id.toIterable(additional)
      )
    }

  override def addUpper(id: Long, rawUpper: ScType,
                        additional: Boolean, variance: Variance): ScUndefinedSubstitutor =
    computeUpper(rawUpper, variance).fold(this: ScUndefinedSubstitutor) { upper =>
      copy(
        upperMap = upperMap.update(id, upper),
        additionalIds = additionalIds ++ id.toIterable(additional)
      )
    }

  private lazy val substWithBounds = substitutionBoundsImpl(nonable = true)

  private lazy val substWithBoundsNotNonable = substitutionBoundsImpl(nonable = false)

  override def substitutionBounds(nonable: Boolean): Option[SubstitutionBounds] =
    if (nonable) substWithBounds
    else substWithBoundsNotNonable

  private def substitutionBoundsImpl(nonable: Boolean): Option[SubstitutionBounds] = {
    var tvMap = LongMap.empty[ScType]
    var lMap = LongMap.empty[ScType]
    var uMap = LongMap.empty[ScType]

    def solve(id: Long, visited: Set[Long]): Option[ScType] = {

      def checkRecursive(tp: ScType, needTvMap: Ref[Boolean]): Boolean = {
        tp.visitRecursively {
          case tpt: TypeParameterType =>
            val otherId = tpt.typeParamId
            if (additionalIds.contains(otherId)) {
              needTvMap.set(true)
              solve(otherId, visited + id) match {
                case None if nonable => return false
                case _ =>
              }
            }
          case UndefinedType(tp, _) =>
            val otherId = tp.typeParamId
            if (typeParamIds.contains(otherId)) {
              needTvMap.set(true)
              solve(otherId, visited + id) match {
                case None if nonable => return false
                case _ =>
              }
            }
          case _: ScType =>
        }
        true
      }

      def hasRecursion(set: Set[ScType], needTvMapRef: Ref[Boolean]): Boolean = {
        val iterator = set.iterator
        while (iterator.hasNext) {
          val p = iterator.next()
          if (!checkRecursive(p, needTvMapRef)) {
            tvMap += ((id, Nothing))
            return true
          }
        }
        false
      }

      if (visited.contains(id)) {
        tvMap += ((id, Nothing))
        return None
      }

      tvMap.get(id) match {
        case Some(tp) => Some(tp)
        case _ =>
          val lowerSet = lowerMap.getOrElse(id, Set.empty)
          if (lowerSet.nonEmpty) {
            val needTvMap = Ref.create(false)
            if (hasRecursion(lowerSet, needTvMap)) return None

            val subst = if (needTvMap.get()) ScSubstitutor(tvMap) else ScSubstitutor.empty

            val substed = lowerSet.map(subst.subst)
            val lower = substed.reduce(_ lub _)

            lMap += ((id, lower))
            tvMap += ((id, lower))
          }

          val upperSet = upperMap.getOrElse(id, Set.empty)
          if (upperSet.nonEmpty) {
            val needTvMap = Ref.create(false)
            if (hasRecursion(upperSet, needTvMap)) return None

            val subst = if (needTvMap.get()) ScSubstitutor(tvMap) else ScSubstitutor.empty
            val substed = upperSet.map(subst.subst)

            val upper = substed.reduce(_ glb _)
            uMap += ((id, upper))

            tvMap.get(id) match {
              case Some(lower) =>
                if (nonable && !lower.conforms(upper)) {
                  return None
                }
              case None => tvMap += ((id, upper))
            }
          }


          if (tvMap.get(id).isEmpty) {
            tvMap += ((id, Nothing))
          }
          tvMap.get(id)
      }
    }

    val idsIterator = typeParamIds.iterator
    while (idsIterator.hasNext) {
      val id = idsIterator.next()
      solve(id, Set.empty) match {
        case Some(_) => // do nothing
        case None if nonable => return None
        case _ =>
      }
    }

    Some(SubstitutionBounds(tvMap, lMap, uMap))
  }

  override def removeTypeParamIds(ids: Set[Long]): ScUndefinedSubstitutor = copy(
    upperMap = upperMap.removeIds(ids),
    lowerMap = lowerMap.removeIds(ids)
  )
}

private object ScUndefinedSubstitutorImpl {

  private implicit class LongMapExt(val map: LongMap[Set[ScType]]) extends AnyVal {

    def getOrDefault(id: Long): Set[ScType] = map.getOrElse(id, Set.empty)

    def removeIds(set: Set[Long]): LongMap[Set[ScType]] = map.filterNot {
      case (long, _) => set(long)
    }

    def update(id: Long, scType: ScType): LongMap[Set[ScType]] =
      map.updated(id, getOrDefault(id) + scType)

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

  private def computeUpper(rawUpper: ScType, v: Variance)
                          (implicit context: ProjectContext) = {
    var index = 0
    val updated = rawUpper match {
      case ScAbstractType(_, _, absUpper) if v == Invariant =>
        absUpper // lower will be added separately
      case ScAbstractType(_, _, absUpper) if v == Covariant && absUpper.equiv(Any) => Any
      case _ =>
        rawUpper.recursiveVarianceUpdate({
          case (ScAbstractType(_, lower, absUpper), variance) =>
            variance match {
              case Contravariant => ReplaceWith(lower)
              case Covariant => ReplaceWith(absUpper)
              case Invariant =>
                index += 1
                ReplaceWith(ScExistentialArgument(s"_$$$index", Nil, lower, absUpper)) //todo: why this is right?
            }
          case (ScExistentialArgument(_, _, lower, skoUpper), variance) =>
            variance match {
              case Contravariant => ReplaceWith(lower)
              case Covariant => ReplaceWith(skoUpper)
              case Invariant =>
                index += 1
                ReplaceWith(ScExistentialArgument(s"_$$$index", Nil, lower, skoUpper))
            }
          case (_: ScExistentialType, _) => Stop
          case _ => ProcessSubtypes
        }, v)
    }

    unpackedType(updated)(_.equiv(Any))
  }

  private def computeLower(rawLower: ScType, v: Variance)
                          (implicit context: ProjectContext) = {
    var index = 0
    val updated = rawLower match {
      case ScAbstractType(_, absLower, _) =>
        absLower //upper will be added separately
      case _ =>
        rawLower.recursiveVarianceUpdate({
          case (ScAbstractType(_, absLower, upper), variance) =>
            variance match {
              case Contravariant => ReplaceWith(absLower)
              case Covariant => ReplaceWith(upper)
              case Invariant => ReplaceWith(absLower /*ScExistentialArgument(s"_$$${index += 1; index}", Nil, absLower, upper)*/) //todo: why this is right?
            }
          case (ScExistentialArgument(_, _, skoLower, upper), variance) =>
            variance match {
              case Contravariant => ReplaceWith(skoLower)
              case Covariant => ReplaceWith(upper)
              case Invariant =>
                index += 1
                ReplaceWith(ScExistentialArgument(s"_$$$index", Nil, skoLower, upper))
            }
          case (_: ScExistentialType, _) => Stop
          case _ => ProcessSubtypes
        }, v, revertVariances = true)
    }

    unpackedType(updated)(_.equiv(Nothing))
  }

  private[this] def unpackedType(`type`: ScType)
                                (predicate: ScType => Boolean) =
    Some(`type`.unpackedType).filterNot(predicate)
}

private final case class ScMultiUndefinedSubstitutor(impls: Set[ScUndefinedSubstitutorImpl])
                                                    (implicit project: ProjectContext)
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

  override def substitutionBounds(nonable: Boolean): Option[ScUndefinedSubstitutor.SubstitutionBounds] =
    impls.iterator.map {
      _.substitutionBounds(nonable)
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