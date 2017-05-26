package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.lang.psi.types.ScUndefinedSubstitutor._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.project.ProjectContext

sealed trait ScUndefinedSubstitutor {
  def addLower(name: Name, _lower: ScType, additional: Boolean = false, variance: Int = -1): ScUndefinedSubstitutor
  def addUpper(name: Name, _upper: ScType, additional: Boolean = false, variance: Int = 1): ScUndefinedSubstitutor
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

  def apply(upperMap: Map[(String, Long), Set[ScType]] = Map.empty,
            lowerMap: Map[(String, Long), Set[ScType]] = Map.empty,
            upperAdditionalMap: Map[(String, Long), Set[ScType]] = Map.empty,
            lowerAdditionalMap: Map[(String, Long), Set[ScType]] = Map.empty)
           (implicit project: ProjectContext): ScUndefinedSubstitutor = {

    new ScUndefinedSubstitutorImpl(upperMap, lowerMap, upperAdditionalMap, lowerAdditionalMap)
  }

  def apply()(implicit project: ProjectContext): ScUndefinedSubstitutor = new ScUndefinedSubstitutorImpl()

  def multi(subs: Seq[ScUndefinedSubstitutor]) = new ScMultiUndefinedSubstitutor(subs)
}

private class ScUndefinedSubstitutorImpl(val upperMap: Map[(String, Long), Set[ScType]] = Map.empty,
                                         val lowerMap: Map[(String, Long), Set[ScType]] = Map.empty,
                                         val upperAdditionalMap: Map[(String, Long), Set[ScType]] = Map.empty,
                                         val lowerAdditionalMap: Map[(String, Long), Set[ScType]] = Map.empty)
                                        (implicit project: ProjectContext)
  extends ScUndefinedSubstitutor {

  def copy(upperMap: Map[(String, Long), Set[ScType]] = upperMap,
           lowerMap: Map[(String, Long), Set[ScType]] = lowerMap,
           upperAdditionalMap: Map[(String, Long), Set[ScType]] = upperAdditionalMap,
           lowerAdditionalMap: Map[(String, Long), Set[ScType]] = lowerAdditionalMap): ScUndefinedSubstitutor = {
    ScUndefinedSubstitutor(upperMap, lowerMap, upperAdditionalMap, lowerAdditionalMap)
  }

  def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty && upperAdditionalMap.isEmpty && lowerAdditionalMap.isEmpty

  //todo: this is can be rewritten in more fast way
  def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = {
    added match {
      case subst: ScUndefinedSubstitutorImpl =>
        var res: ScUndefinedSubstitutor = this
        for ((name, seq) <- subst.upperMap) {
          for (upper <- seq) {
            res = res.addUpper(name, upper, variance = 0)
          }
        }
        for ((name, seq) <- subst.lowerMap) {
          for (lower <- seq) {
            res = res.addLower(name, lower, variance = 0)
          }
        }

        for ((name, seq) <- subst.upperAdditionalMap) {
          for (upper <- seq) {
            res = res.addUpper(name, upper, additional = true, variance = 0)
          }
        }
        for ((name, seq) <- subst.lowerAdditionalMap) {
          for (lower <- seq) {
            res = res.addLower(name, lower, additional = true, variance = 0)
          }
        }

        res
      case subst: ScMultiUndefinedSubstitutor =>
        subst.addSubst(this)
    }
  }

  def addLower(name: Name, _lower: ScType, additional: Boolean = false, variance: Int = -1): ScUndefinedSubstitutor = {
    var index = 0
    val lower = (_lower match {
      case ScAbstractType(_, absLower, _) =>
        if (absLower.equiv(Nothing)) return this
        absLower //upper will be added separately
      case _ =>
        _lower.recursiveVarianceUpdateModifiable[Set[String]](Set.empty, {
          case (ScAbstractType(_, absLower, upper), i, data) =>
            i match {
              case -1 => (true, absLower, data)
              case 1 => (true, upper, data)
              case 0 => (true, absLower/*ScExistentialArgument(s"_$$${index += 1; index}", Nil, absLower, upper)*/, data) //todo: why this is right?
            }
          case (ScExistentialArgument(nm, _, skoLower, upper), i, data) if !data.contains(nm) =>
            i match {
              case -1 => (true, skoLower, data)
              case 1 => (true, upper, data)
              case 0 => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, skoLower, upper), data)
            }
          case (ex: ScExistentialType, _, data) => (false, ex, data ++ ex.boundNames)
          case (tp, _, data) => (false, tp, data)
        }, variance)
    }).unpackedType

    addToMap(name, lower, toUpper = false, additional)
  }

  def addUpper(name: Name, _upper: ScType, additional: Boolean = false, variance: Int = 1): ScUndefinedSubstitutor = {
    var index = 0
    val upper =
      (_upper match {
        case ScAbstractType(_, _, absUpper) if variance == 0 =>
          if (absUpper.equiv(Any)) return this
          absUpper // lower will be added separately
        case ScAbstractType(_, _, absUpper) if variance == 1 && absUpper.equiv(Any) => return this
        case _ =>
          _upper.recursiveVarianceUpdateModifiable[Set[String]](Set.empty, {
            case (ScAbstractType(_, lower, absUpper), i, data) =>
              i match {
                case -1 => (true, lower, data)
                case 1 => (true, absUpper, data)
                case 0 => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, lower, absUpper), data) //todo: why this is right?
              }
            case (ScExistentialArgument(nm, _, lower, skoUpper), i, data) if !data.contains(nm) =>
              i match {
                case -1 => (true, lower, data)
                case 1 => (true, skoUpper, data)
                case 0 => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, lower, skoUpper), data)
              }
            case (ex: ScExistentialType, _, data) => (false, ex, data ++ ex.boundNames)
            case (tp, _, data) => (false, tp, data)
          }, variance)
      }).unpackedType

    addToMap(name, upper, toUpper = true, additional)
  }

  lazy val additionalNames: Set[Name] = {
    //We need to exclude Nothing names from this set, see SCL-5736
    lowerAdditionalMap.filter(_._2.exists(!_.equiv(Nothing))).keySet ++ upperAdditionalMap.keySet
  }

  lazy val names: Set[Name] = {
    //We need to exclude Nothing names from this set, see SCL-5736
    upperMap.keySet ++ lowerMap.filter(_._2.exists(!_.equiv(Nothing))).keySet ++ additionalNames
  }

  private lazy val substWithBounds: Option[SubstitutorWithBounds] = getSubstitutorWithBoundsImpl(nonable = true)

  private lazy val substWithBoundsNotNonable: Option[SubstitutorWithBounds] = getSubstitutorWithBoundsImpl(nonable = false)

  override def getSubstitutorWithBounds(nonable: Boolean): Option[SubstitutorWithBounds] = {
    if (nonable) substWithBounds
    else substWithBoundsNotNonable
  }

  private def addToMap(name: Name, scType: ScType, toUpper: Boolean, toAdditional: Boolean): ScUndefinedSubstitutor = {
    val map =
      if (toUpper) if (toAdditional) upperAdditionalMap else upperMap
      else if (toAdditional) lowerAdditionalMap else lowerMap

    val forName = map.getOrElse(name, Set.empty)
    val updated = map.updated(name, forName + scType)

    if (toUpper)
      if (toAdditional) copy(upperAdditionalMap = updated)
      else copy(upperMap = updated)
    else
      if (toAdditional) copy(lowerAdditionalMap = updated)
      else copy(lowerMap = updated)
  }

  private def getSubstitutorWithBoundsImpl(nonable: Boolean): Option[SubstitutorWithBounds] = {
    var tvMap = Map.empty[Name, ScType]
    var lMap = Map.empty[Name, ScType]
    var uMap = Map.empty[Name, ScType]

    def solve(name: Name, visited: Set[Name]): Option[ScType] = {

      def checkRecursive(tp: ScType, needTvMapRef: Ref[Boolean]): Boolean = {
        tp.recursiveUpdate {
          case tpt: TypeParameterType =>
            val otherName = tpt.nameAndId
            if (additionalNames.contains(otherName)) {
              needTvMapRef.set(true)
              solve(otherName, visited + name) match {
                case None if nonable => return false
                case _ =>
              }
            }
            (false, tpt)
          case UndefinedType(tpt, _) =>
            val otherName = tpt.nameAndId
            if (names.contains(otherName)) {
              needTvMapRef.set(true)
              solve(otherName, visited + name) match {
                case None if nonable => return false
                case _ =>
              }
            }
            (false, tpt)
          case tp: ScType => (false, tp)
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
          val lowerSet = lowerMap.getOrElse(name, Set.empty) ++ lowerAdditionalMap.getOrElse(name, Set.empty)
          if (lowerSet.nonEmpty) {
            val needTvMapRef = Ref.create(false)
            if (hasRecursion(lowerSet, needTvMapRef)) return None

            val subst = if (needTvMapRef.get()) ScSubstitutor(tvMap) else ScSubstitutor.empty

            val substed = lowerSet.toSeq.map(subst.subst)
            val lower = project.typeSystem.lub(substed, checkWeak = true)

            lMap += ((name, lower))
            tvMap += ((name, lower))
          }

          val upperSet = upperMap.getOrElse(name, Set.empty) ++ upperAdditionalMap.getOrElse(name, Set.empty)
          if (upperSet.nonEmpty) {
            val needTvMapRef = Ref.create(false)
            if (hasRecursion(upperSet, needTvMapRef)) return None

            val subst = if (needTvMapRef.get()) ScSubstitutor(tvMap) else ScSubstitutor.empty
            val substed = upperSet.toSeq.map(subst.subst)

            val upper = project.typeSystem.glb(substed, checkWeak = false)
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
    ScUndefinedSubstitutor(
      upperMap.filter(fun),
      lowerMap.filter(fun),
      upperAdditionalMap.filter(fun),
      lowerAdditionalMap.filter(fun))
  }
}

class ScMultiUndefinedSubstitutor(val subs: Seq[ScUndefinedSubstitutor]) extends ScUndefinedSubstitutor {
  def copy(subs: Seq[ScUndefinedSubstitutor]) = new ScMultiUndefinedSubstitutor(subs)

  override def addLower(name: (String, Long), _lower: ScType, additional: Boolean, variance: Int): ScUndefinedSubstitutor =
    copy(subs.map(_.addLower(name, _lower, additional, variance)))

  override def addUpper(name: (String, Long), _upper: ScType, additional: Boolean, variance: Int): ScUndefinedSubstitutor =
    copy(subs.map(_.addUpper(name, _upper, additional, variance)))

  override def getSubstitutorWithBounds(notNonable: Boolean): Option[SubstitutorWithBounds] =
    subs.map(_.getSubstitutorWithBounds(notNonable)).find(_.isDefined).getOrElse(None)

  override def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor =
    copy(subs.map(_.filter(fun)))

  override def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = copy(subs.map(_.addSubst(added)))

  override def isEmpty: Boolean = subs.forall(_.isEmpty)

  override def names: Set[(String, Long)] = if (subs.isEmpty) Set.empty else subs.tail.map(_.names).
    foldLeft(subs.head.names){case (a, b) => a.intersect(b)}
}