package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.lang.psi.types.ScUndefinedSubstitutor._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.project.ProjectContext

sealed trait ScUndefinedSubstitutor {
  def addLower(name: Name, _lower: ScType, additional: Boolean = false, variance: Variance = Contravariant): ScUndefinedSubstitutor
  def addUpper(name: Name, _upper: ScType, additional: Boolean = false, variance: Variance = Covariant): ScUndefinedSubstitutor
  def getSubstitutor: Option[ScSubstitutor] = getSubstitutorWithBounds(nonable = true).map(_._1)
  def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor
  def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor
  def +(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = addSubst(subst)
  def isEmpty: Boolean
  def names: Set[Name]

  //subst, lowers, uppers
  def getSubstitutorWithBounds(nonable: Boolean): Option[(ScSubstitutor, Map[Name, ScType], Map[Name, ScType])]
}

object ScUndefinedSubstitutor {

  type Name = (String, Long)
  type SubstitutorWithBounds = (ScSubstitutor, Map[Name, ScType], Map[Name, ScType])

  def apply()(implicit project: ProjectContext): ScUndefinedSubstitutor = {
    ScUndefinedSubstitutorImpl(Map.empty, Map.empty, Set.empty)
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

private case class ScUndefinedSubstitutorImpl(upperMap: Map[Name, Set[ScType]] = Map.empty,
                                              lowerMap: Map[Name, Set[ScType]] = Map.empty,
                                              additionalNames: Set[Name] = Set.empty)
                                             (implicit project: ProjectContext)
  extends ScUndefinedSubstitutor {

  def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty

  private def equivNothing(tp: ScType) = tp.equiv(Nothing(tp.projectContext))

  private def equivAny(tp: ScType) = tp.equiv(Any(tp.projectContext))

  private def merge(map1: Map[Name, Set[ScType]], map2: Map[Name, Set[ScType]], forUpper: Boolean): Map[Name, Set[ScType]] = {
    var result = Map[Name, Set[ScType]]()
    val iterator = map1.iterator ++ map2.iterator
    while (iterator.nonEmpty) {
      val (name, set) = iterator.next()
      val newSet = result.getOrElse(name, Set.empty) ++ set
      val filtered = if (forUpper) newSet.filterNot(equivAny) else newSet.filterNot(equivNothing)
      result =
        if (filtered.isEmpty) result - name
        else result.updated(name, filtered)
    }
    result
  }

  def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = {
    added match {
      case subst: ScUndefinedSubstitutorImpl =>
        val newUpper = merge(this.upperMap, subst.upperMap, forUpper = true)
        val newLower = merge(this.lowerMap, subst.lowerMap, forUpper = false)
        val newAddNames = this.additionalNames ++ subst.additionalNames
        ScUndefinedSubstitutorImpl(newUpper, newLower, newAddNames)
      case subst: ScMultiUndefinedSubstitutor =>
        subst.addSubst(this)
    }
  }

  def addLower(name: Name, _lower: ScType, additional: Boolean = false, variance: Variance = Contravariant): ScUndefinedSubstitutor = {
    val lower = computeLower(_lower, variance)

    if (equivNothing(lower)) this
    else addToMap(name, lower, toUpper = false, additional)
  }

  def addUpper(name: Name, _upper: ScType, additional: Boolean = false, variance: Variance = Covariant): ScUndefinedSubstitutor = {
    val upper = computeUpper(_upper, variance)

    if (equivAny(upper)) this
    else addToMap(name, upper, toUpper = true, additional)
  }

  lazy val names: Set[Name] = upperMap.keySet ++ lowerMap.keySet

  private lazy val substWithBounds: Option[SubstitutorWithBounds] = getSubstitutorWithBoundsImpl(nonable = true)

  private lazy val substWithBoundsNotNonable: Option[SubstitutorWithBounds] = getSubstitutorWithBoundsImpl(nonable = false)

  override def getSubstitutorWithBounds(nonable: Boolean): Option[SubstitutorWithBounds] = {
    if (nonable) substWithBounds
    else substWithBoundsNotNonable
  }

  private def addToMap(name: Name, scType: ScType, toUpper: Boolean, toAdditional: Boolean): ScUndefinedSubstitutor = {
    val map = if (toUpper) upperMap else lowerMap

    val forName = map.getOrElse(name, Set.empty)
    val updated = map.updated(name, forName + scType)

    val additional = if (toAdditional) additionalNames + name else additionalNames

    if (toUpper) copy(upperMap = updated, additionalNames = additional)
    else copy(lowerMap = updated, additionalNames = additional)
  }

  private def getSubstitutorWithBoundsImpl(nonable: Boolean): Option[SubstitutorWithBounds] = {
    var tvMap = Map.empty[Name, ScType]
    var lMap = Map.empty[Name, ScType]
    var uMap = Map.empty[Name, ScType]

    def solve(name: Name, visited: Set[Name]): Option[ScType] = {

      def checkRecursive(tp: ScType, needTvMap: Ref[Boolean]): Boolean = {
        tp.visitRecursively {
          case tpt: TypeParameterType =>
            val otherName = tpt.nameAndId
            if (additionalNames.contains(otherName)) {
              needTvMap.set(true)
              solve(otherName, visited + name) match {
                case None if nonable => return false
                case _ =>
              }
            }
          case UndefinedType(tpt, _) =>
            val otherName = tpt.nameAndId
            if (names.contains(otherName)) {
              needTvMap.set(true)
              solve(otherName, visited + name) match {
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
            tvMap += ((name, Nothing))
            return true
          }
        }
        false
      }

      if (visited.contains(name)) {
        tvMap += ((name, Nothing))
        return None
      }

      tvMap.get(name) match {
        case Some(tp) => Some(tp)
        case _ =>
          val lowerSet = lowerMap.getOrElse(name, Set.empty)
          if (lowerSet.nonEmpty) {
            val needTvMap = Ref.create(false)
            if (hasRecursion(lowerSet, needTvMap)) return None

            val subst = if (needTvMap.get()) ScSubstitutor(tvMap) else ScSubstitutor.empty

            val substed = lowerSet.map(subst.subst)
            val lower = substed.reduce(_ lub _)

            lMap += ((name, lower))
            tvMap += ((name, lower))
          }

          val upperSet = upperMap.getOrElse(name, Set.empty)
          if (upperSet.nonEmpty) {
            val needTvMap = Ref.create(false)
            if (hasRecursion(upperSet, needTvMap)) return None

            val subst = if (needTvMap.get()) ScSubstitutor(tvMap) else ScSubstitutor.empty
            val substed = upperSet.map(subst.subst)

            val upper = substed.reduce(_ glb _)
            uMap += ((name, upper))

            tvMap.get(name) match {
              case Some(lower) =>
                if (nonable && !lower.conforms(upper)) {
                  return None
                }
              case None => tvMap += ((name, upper))
            }
          }


          if (tvMap.get(name).isEmpty) {
            tvMap += ((name, Nothing))
          }
          tvMap.get(name)
      }
    }

    val namesIterator = names.iterator
    while (namesIterator.hasNext) {
      val name = namesIterator.next()
      solve(name, Set.empty) match {
        case Some(_) => // do nothing
        case None if nonable => return None
        case _ =>
      }
    }
    val subst = ScSubstitutor(tvMap)
    Some((subst, lMap, uMap))
  }

  def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor = {
    copy(upperMap = upperMap.filter(fun), lowerMap = lowerMap.filter(fun))
  }
}

private case class ScMultiUndefinedSubstitutor(subs: Set[ScUndefinedSubstitutorImpl])(implicit project: ProjectContext)
  extends ScUndefinedSubstitutor {

  override def addLower(name: (String, Long), _lower: ScType, additional: Boolean, variance: Variance): ScUndefinedSubstitutor =
    multi(subs.map(_.addLower(name, _lower, additional, variance)))

  override def addUpper(name: (String, Long), _upper: ScType, additional: Boolean, variance: Variance): ScUndefinedSubstitutor =
    multi(subs.map(_.addUpper(name, _upper, additional, variance)))

  override def getSubstitutorWithBounds(nonable: Boolean): Option[SubstitutorWithBounds] =
    subs.iterator.map(_.getSubstitutorWithBounds(nonable)).find(_.isDefined).flatten

  override def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor =
    multi(subs.map(_.filter(fun)))

  override def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = added match {
    case impl: ScUndefinedSubstitutorImpl =>
      multi(subs.map(_.addSubst(impl)))
    case mult: ScMultiUndefinedSubstitutor =>
      val flatten = for (s1 <- subs; s2 <- mult.subs) yield s1.addSubst(s2)
      multi(flatten)
  }

  override def isEmpty: Boolean = names.isEmpty

  override val names: Set[Name] = subs.map(_.names).reduce(_ intersect _)
}