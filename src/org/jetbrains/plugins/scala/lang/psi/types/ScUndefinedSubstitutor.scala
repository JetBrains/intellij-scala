package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.project.ProjectContext

sealed trait ScUndefinedSubstitutor {

  type Name = (String, Long)

  case class SubstitutorWithBounds(subst: ScSubstitutor, lowers: Map[Name, ScType], upper: Map[Name, ScType])

  def addLower(name: Name, _lower: ScType, additional: Boolean = false, variance: Int = -1): ScUndefinedSubstitutor
  def addUpper(name: Name, _upper: ScType, additional: Boolean = false, variance: Int = 1): ScUndefinedSubstitutor
  def getSubstitutor(notNonable: Boolean): Option[ScSubstitutor] = getSubstitutorWithBounds(notNonable).map(_._1)
  def getSubstitutor: Option[ScSubstitutor] = getSubstitutor(notNonable = false)
  def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor
  def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor
  def +(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = addSubst(subst)
  def isEmpty: Boolean
  def names: Set[Name]

  //subst, lowers, uppers
  def getSubstitutorWithBounds(notNonable: Boolean): Option[(ScSubstitutor, Map[Name, ScType], Map[Name, ScType])]
}

object ScUndefinedSubstitutor {
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
    val lMap = if (additional) lowerAdditionalMap else lowerMap
    lMap.get(name) match {
      case Some(set: Set[ScType]) =>
        if (additional) copy(lowerAdditionalMap = lMap.updated(name, set + lower))
        else copy(lowerMap = lMap.updated(name, set + lower))
      case None =>
        if (additional) copy(lowerAdditionalMap = lMap + ((name, Set(lower))))
        else copy(lowerMap = lMap + ((name, Set(lower))))
    }
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
    val uMap = if (additional) upperAdditionalMap else upperMap
    uMap.get(name) match {
      case Some(set: Set[ScType]) =>
        if (additional) copy(upperAdditionalMap = uMap.updated(name, set + upper))
        else copy(upperMap = uMap.updated(name, set + upper))
      case None =>
        if (additional) copy(upperAdditionalMap = uMap + ((name, Set(upper))))
        else copy(upperMap = uMap + ((name, Set(upper))))
    }
  }

  lazy val additionalNames: Set[Name] = {
    //We need to exclude Nothing names from this set, see SCL-5736
    lowerAdditionalMap.filter(_._2.exists(!_.equiv(Nothing))).keySet ++ upperAdditionalMap.keySet
  }

  lazy val names: Set[Name] = {
    //We need to exclude Nothing names from this set, see SCL-5736
    upperMap.keySet ++ lowerMap.filter(_._2.exists(!_.equiv(Nothing))).keySet ++ additionalNames
  }

  def getSubstitutorWithBounds(notNonable: Boolean): Option[(ScSubstitutor, Map[Name, ScType], Map[Name, ScType])] = {
    var tvMap = Map.empty[Name, ScType]
    var lMap = Map.empty[Name, ScType]
    var uMap = Map.empty[Name, ScType]

    def solve(name: Name, visited: Set[Name]): Option[ScType] = {
      if (visited.contains(name)) {
        tvMap += ((name, Nothing))
        return None
      }
      tvMap.get(name) match {
        case Some(tp) => Some(tp)
        case _ =>
          (lowerMap.get(name).map(set => lowerAdditionalMap.get(name) match {
            case Some(set1) => set ++ set1
            case _ => set
          }) match {
            case Some(set) => Some(set)
            case _ => lowerAdditionalMap.get(name)
          }) match {
            case Some(set) =>
              var res = false
              def checkRecursive(tp: ScType): Boolean = {
                tp.recursiveUpdate {
                  case tpt: TypeParameterType =>
                    val otherName = tpt.nameAndId
                    if (additionalNames.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case UndefinedType(tpt, _) =>
                    val otherName = tpt.nameAndId
                    if (names.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case tp: ScType => (false, tp)
                }
                true
              }
              val seqIterator = set.iterator
              while (seqIterator.hasNext) {
                val p = seqIterator.next()
                if (!checkRecursive(p)) {
                  tvMap += ((name, Nothing))
                  return None
                }
              }
              if (set.nonEmpty) {
                val subst = if (res) ScSubstitutor(tvMap) else ScSubstitutor.empty
                var lower: ScType = Nothing
                val setIterator = set.iterator
                while (setIterator.hasNext) {
                  lower = lower.lub(subst.subst(setIterator.next()), checkWeak = true)
                }
                lMap += ((name, lower))
                tvMap += ((name, lower))
              }
            case None =>
          }
          (upperMap.get(name).map(set => upperAdditionalMap.get(name) match {
            case Some(set1) => set ++ set1
            case _ => set
          }) match {
            case Some(set) => Some(set)
            case _ => upperAdditionalMap.get(name)
          }) match {
            case Some(set) =>
              var res = false
              def checkRecursive(tp: ScType): Boolean = {
                tp.recursiveUpdate {
                  case tpt: TypeParameterType =>
                    val otherName = tpt.nameAndId
                    if (additionalNames.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case UndefinedType(tpt, _) =>
                    val otherName = tpt.nameAndId
                    if (names.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case tp: ScType => (false, tp)
                }
                true
              }
              val seqIterator = set.iterator
              while (seqIterator.hasNext) {
                val p = seqIterator.next()
                if (!checkRecursive(p)) {
                  tvMap += ((name, Nothing))
                  return None
                }
              }
              if (set.nonEmpty) {
                var uType: ScType = Nothing
                val subst = if (res) ScSubstitutor(tvMap) else ScSubstitutor.empty
                val size: Int = set.size
                if (size == 1) {
                  uType = subst.subst(set.iterator.next())
                  uMap += ((name, uType))
                } else if (size > 1) {
                  var upper: ScType = Any
                  val setIterator = set.iterator
                  while (setIterator.hasNext) {
                    upper = upper.glb(subst.subst(setIterator.next()), checkWeak = false)
                  }
                  uType = upper
                  uMap += ((name, uType))
                }
                tvMap.get(name) match {
                  case Some(lower) =>
                    if (!notNonable) {
                      val seqIterator = set.iterator
                      while (seqIterator.hasNext) {
                        val upper = seqIterator.next()
                        if (!lower.conforms(subst.subst(upper))) {
                          return None
                        }
                      }
                    }
                  case None => tvMap += ((name, uType))
                }
              }
            case None =>
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
        case None if !notNonable => return None
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

  override def getSubstitutorWithBounds(notNonable: Boolean): Option[(ScSubstitutor, Map[Name, ScType], Map[Name, ScType])] =
    subs.map(_.getSubstitutorWithBounds(notNonable)).find(_.isDefined).getOrElse(None)

  override def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor =
    copy(subs.map(_.filter(fun)))

  override def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = copy(subs.map(_.addSubst(added)))

  override def isEmpty: Boolean = subs.forall(_.isEmpty)

  override def names: Set[(String, Long)] = if (subs.isEmpty) Set.empty else subs.tail.map(_.names).
    foldLeft(subs.head.names){case (a, b) => a.intersect(b)}
}