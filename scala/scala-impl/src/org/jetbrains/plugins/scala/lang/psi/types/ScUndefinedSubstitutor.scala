package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScUndefinedSubstitutor._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.immutable.LongMap

sealed trait ScUndefinedSubstitutor {
  def addLower(id: Long, _lower: ScType, additional: Boolean = false, variance: Variance = Contravariant): ScUndefinedSubstitutor
  def addUpper(id: Long, _upper: ScType, additional: Boolean = false, variance: Variance = Covariant): ScUndefinedSubstitutor
  def getSubstitutor: Option[ScSubstitutor] = getSubstitutorWithBounds(nonable = true).map(_._1)
  def filterTypeParamIds(fun: Long => Boolean): ScUndefinedSubstitutor
  def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor
  def +(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = addSubst(subst)
  def isEmpty: Boolean
  def typeParamIds: Set[Long]

  def getSubstitutorWithBounds(nonable: Boolean): Option[SubstitutorWithBounds]
}

object ScUndefinedSubstitutor {

  //subst, lowers, uppers
  type SubstitutorWithBounds = (ScSubstitutor, LongMap[ScType], LongMap[ScType])

  def apply()(implicit project: ProjectContext): ScUndefinedSubstitutor = {
    ScUndefinedSubstitutorImpl(LongMap.empty, LongMap.empty, Set.empty)
  }

  def multi(subs: Set[ScUndefinedSubstitutor])(implicit project: ProjectContext): ScUndefinedSubstitutor = {
    val flatten = subs.filterNot(_.isEmpty).flatMap {
      case m: ScMultiUndefinedSubstitutor => m.subs
      case s: ScUndefinedSubstitutorImpl => Set(s)
    }
    flatten.size match {
      case 0 => ScUndefinedSubstitutor()
      case 1 => flatten.head
      case _ => ScMultiUndefinedSubstitutor(flatten)
    }
  }

  private[types] def computeLower(rawLower: ScType, v: Variance): ScType = {
    var index = 0
    val updated = rawLower match {
      case ScAbstractType(_, absLower, _) =>
        absLower //upper will be added separately
      case _ =>
        rawLower.recursiveVarianceUpdateModifiable[Set[String]](Set.empty, {
          case (ScAbstractType(_, absLower, upper), variance, data) =>
            variance match {
              case Contravariant => (true, absLower, data)
              case Covariant     => (true, upper, data)
              case Invariant     => (true, absLower /*ScExistentialArgument(s"_$$${index += 1; index}", Nil, absLower, upper)*/ , data) //todo: why this is right?
            }
          case (ScExistentialArgument(nm, _, skoLower, upper), variance, data) if !data.contains(nm) =>
            variance match {
              case Contravariant => (true, skoLower, data)
              case Covariant     => (true, upper, data)
              case Invariant     => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, skoLower, upper), data)
            }
          case (ex: ScExistentialType, _, data) => (false, ex, data ++ ex.boundNames)
          case (tp, _, data) => (false, tp, data)
        }, v, revertVariances = true)
    }
    updated.unpackedType
  }

  private[types] def computeUpper(rawUpper: ScType, v: Variance): ScType = {
    import rawUpper.projectContext

    var index = 0
    val updated = rawUpper match {
      case ScAbstractType(_, _, absUpper) if v == Invariant =>
        absUpper // lower will be added separately
      case ScAbstractType(_, _, absUpper) if v == Covariant && absUpper.equiv(Any) => Any
      case _ =>
        rawUpper.recursiveVarianceUpdateModifiable[Set[String]](Set.empty, {
          case (ScAbstractType(_, lower, absUpper), variance, data) =>
            variance match {
              case Contravariant => (true, lower, data)
              case Covariant     => (true, absUpper, data)
              case Invariant     => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, lower, absUpper), data) //todo: why this is right?
            }
          case (ScExistentialArgument(nm, _, lower, skoUpper), variance, data) if !data.contains(nm) =>
            variance match {
              case Contravariant => (true, lower, data)
              case Covariant     => (true, skoUpper, data)
              case Invariant     => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, lower, skoUpper), data)
            }
          case (ex: ScExistentialType, _, data) => (false, ex, data ++ ex.boundNames)
          case (tp, _, data) => (false, tp, data)
        }, v)
    }
    updated.unpackedType
  }
}

private case class ScUndefinedSubstitutorImpl(upperMap: LongMap[Set[ScType]] = LongMap.empty,
                                              lowerMap: LongMap[Set[ScType]] = LongMap.empty,
                                              additionalIds: Set[Long] = Set.empty)
                                             (implicit project: ProjectContext)
  extends ScUndefinedSubstitutor {

  def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty

  private def equivNothing(tp: ScType) = tp.equiv(Nothing(tp.projectContext))

  private def equivAny(tp: ScType) = tp.equiv(Any(tp.projectContext))

  private def merge(map1: LongMap[Set[ScType]], map2: LongMap[Set[ScType]], forUpper: Boolean): LongMap[Set[ScType]] = {
    def removeTrivialBounds(set: Set[ScType]): Option[Set[ScType]] = {
      val filtered =
        if (forUpper) set.filterNot(equivAny)
        else set.filterNot(equivNothing)

      if (filtered.isEmpty) None
      else Some(filtered)
    }

    map1.unionWith(map2, (id, set1, set2) =>
      set1 ++ set2
    ).modifyOrRemove { (id, set) =>
      removeTrivialBounds(set)
    }
  }

  def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = {
    added match {
      case subst: ScUndefinedSubstitutorImpl =>
        val newUpper = merge(this.upperMap, subst.upperMap, forUpper = true)
        val newLower = merge(this.lowerMap, subst.lowerMap, forUpper = false)
        val newAddIds = this.additionalIds ++ subst.additionalIds
        ScUndefinedSubstitutorImpl(newUpper, newLower, newAddIds)
      case subst: ScMultiUndefinedSubstitutor =>
        subst.addSubst(this)
    }
  }

  def addLower(id: Long, _lower: ScType, additional: Boolean = false, variance: Variance = Contravariant): ScUndefinedSubstitutor = {
    val lower = computeLower(_lower, variance)

    if (equivNothing(lower)) this
    else addToMap(id, lower, toUpper = false, additional)
  }

  def addUpper(id: Long, _upper: ScType, additional: Boolean = false, variance: Variance = Covariant): ScUndefinedSubstitutor = {
    val upper = computeUpper(_upper, variance)

    if (equivAny(upper)) this
    else addToMap(id, upper, toUpper = true, additional)
  }

  lazy val typeParamIds: Set[Long] = upperMap.keySet ++ lowerMap.keySet

  private lazy val substWithBounds: Option[SubstitutorWithBounds] = getSubstitutorWithBoundsImpl(nonable = true)

  private lazy val substWithBoundsNotNonable: Option[SubstitutorWithBounds] = getSubstitutorWithBoundsImpl(nonable = false)

  override def getSubstitutorWithBounds(nonable: Boolean): Option[SubstitutorWithBounds] = {
    if (nonable) substWithBounds
    else substWithBoundsNotNonable
  }

  private def addToMap(id: Long, scType: ScType, toUpper: Boolean, toAdditional: Boolean): ScUndefinedSubstitutor = {
    val map = if (toUpper) upperMap else lowerMap

    val forId = map.getOrElse(id, Set.empty)
    val updated = map.updated(id, forId + scType)

    val additional = if (toAdditional) additionalIds + id else additionalIds

    if (toUpper) copy(upperMap = updated, additionalIds = additional)
    else copy(lowerMap = updated, additionalIds = additional)
  }

  private def getSubstitutorWithBoundsImpl(nonable: Boolean): Option[SubstitutorWithBounds] = {
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
          case UndefinedType(tpt, _) =>
            val otherId = tpt.typeParamId
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
    val subst = ScSubstitutor(tvMap)
    Some((subst, lMap, uMap))
  }

  override def filterTypeParamIds(fun: Long => Boolean): ScUndefinedSubstitutor = {
    copy(upperMap = upperMap.filter(entry => fun(entry._1)), lowerMap = lowerMap.filter(entry => fun(entry._1)))
  }
}

private case class ScMultiUndefinedSubstitutor(subs: Set[ScUndefinedSubstitutorImpl])(implicit project: ProjectContext)
  extends ScUndefinedSubstitutor {

  override def addLower(id: Long, _lower: ScType, additional: Boolean, variance: Variance): ScUndefinedSubstitutor =
    multi(subs.map(_.addLower(id, _lower, additional, variance)))

  override def addUpper(id: Long, _upper: ScType, additional: Boolean, variance: Variance): ScUndefinedSubstitutor =
    multi(subs.map(_.addUpper(id, _upper, additional, variance)))

  override def getSubstitutorWithBounds(nonable: Boolean): Option[SubstitutorWithBounds] =
    subs.iterator.map(_.getSubstitutorWithBounds(nonable)).find(_.isDefined).flatten

  override def filterTypeParamIds(fun: Long => Boolean): ScUndefinedSubstitutor =
    multi(subs.map(_.filterTypeParamIds(fun)))

  override def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = added match {
    case impl: ScUndefinedSubstitutorImpl =>
      multi(subs.map(_.addSubst(impl)))
    case mult: ScMultiUndefinedSubstitutor =>
      val flatten = for (s1 <- subs; s2 <- mult.subs) yield s1.addSubst(s2)
      multi(flatten)
  }

  override def isEmpty: Boolean = typeParamIds.isEmpty

  override val typeParamIds: Set[Long] = subs.map(_.typeParamIds).reduce(_ intersect _)
}