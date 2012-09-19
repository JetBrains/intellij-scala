package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.util.Key
import java.lang.String
import nonvalue.{TypeParameter, ScTypePolymorphicType, ScMethodType}
import com.intellij.psi._
import api.toplevel.ScTypedDefinition
import result.TypingContext
import api.toplevel.typedef.ScTypeDefinition
import collection.immutable.{HashMap, Map}

/**
* @author ven
*/
object ScSubstitutor {
  val empty: ScSubstitutor = new ScSubstitutor()

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")

  private val followLimit = 800
}

class ScSubstitutor(val tvMap: Map[(String, String), ScType],
                    val aliasesMap: Map[String, Suspension[ScType]],
                    val updateThisType: Option[ScType]) {
  def this() = this(Map.empty, Map.empty, None)

  def this(updateThisType: ScType) {
    this(Map.empty, Map.empty, Some(updateThisType))
  }

  def this(tvMap: Map[(String, String), ScType],
                    aliasesMap: Map[String, Suspension[ScType]],
                    updateThisType: Option[ScType],
                    follower: ScSubstitutor) = {
    this(tvMap, aliasesMap, updateThisType)
    this.follower = follower
  }

  private var follower: ScSubstitutor = null

  def getFollower: ScSubstitutor = follower

  override def toString: String = "ScSubstitutor(" + tvMap + ", " + aliasesMap + ")" +
    (if (follower != null) " followed " + follower.toString else "")

  def bindT(name : (String, String), t: ScType) =
    new ScSubstitutor(tvMap + ((name, t)), aliasesMap, updateThisType, follower)
  def bindA(name: String, t: ScType) =
    new ScSubstitutor(tvMap, aliasesMap + ((name, new Suspension[ScType](t))), updateThisType, follower)
  def bindA(name: String, f: () => ScType) =
    new ScSubstitutor(tvMap, aliasesMap + ((name, new Suspension[ScType](f))), updateThisType, follower)
  def addUpdateThisType(tp: ScType): ScSubstitutor = {
    this followed (new ScSubstitutor(Map.empty, Map.empty, Some(tp)))
  }
  def followed(s: ScSubstitutor): ScSubstitutor = followed(s, 0)

  private def followed(s: ScSubstitutor, level: Int): ScSubstitutor = {
    if (level > ScSubstitutor.followLimit)
      throw new RuntimeException("Too much followers for substitutor: " + this.toString)
    if (follower == null && tvMap.size + aliasesMap.size  == 0 && updateThisType == None) s
    else if (s.getFollower == null && s.tvMap.size + s.aliasesMap.size == 0 && s.updateThisType == None) this
    else new ScSubstitutor(tvMap, aliasesMap, updateThisType,
      if (follower != null) follower followed (s, level + 1) else s)
  }

  def subst(t: ScType): ScType = try {
    if (follower != null) follower.subst(substInternal(t)) else substInternal(t)
  }
  catch {
    case s: StackOverflowError =>
      throw new RuntimeException("StackOverFlow during ScSubstitutor.subst(" + t + ") this = " + this, s)
  }

  private def extractTpt(tpt: ScTypeParameterType, t: ScType): ScType = {
    if (tpt.args.length == 0) t
    else t match {
      case ScParameterizedType(t, _) => t
      case _ => t
    }
  }

  protected def substInternal(t: ScType) : ScType = {
    t match {
      case f@ScFunctionType(ret, params) => new ScFunctionType(substInternal(ret), params.map(substInternal(_)))(f.getProject, f.getScope)
      case t1@ScTupleType(comps) => new ScTupleType(comps.map(substInternal))(t1.getProject, t1.getScope)
      case ScProjectionType(proj: ScThisType, element, subst, true) => new ScProjectionType(substInternal(proj), element, subst, false)
      case ScProjectionType(proj, element, subst, s) => new ScProjectionType(substInternal(proj), element, subst, s)
      case m@ScMethodType(retType, params, isImplicit) => new ScMethodType(substInternal(retType),
        params.map(p => p.copy(paramType = substInternal(p.paramType), expectedType = substInternal(p.expectedType))), isImplicit)(m.project, m.scope)
      case ScTypePolymorphicType(internalType, typeParameters) => {
        ScTypePolymorphicType(substInternal(internalType), typeParameters.map(tp => {
          TypeParameter(tp.name, substInternal(tp.lowerType), substInternal(tp.upperType), tp.ptp)
        }))
      }
      case ScThisType(clazz) =>
        updateThisType match {
          case Some(oldTp) => {
            var tp = oldTp
            def update(typez: ScType): ScType = {
              ScType.extractDesignated(typez) match {
                case Some((t: ScTypeDefinition, subst)) =>
                  if (t == clazz) tp
                  else if (ScalaPsiUtil.cachedDeepIsInheritor(t, clazz)) tp 
                  else {
                    t.selfType match {
                      case Some(selfType) =>
                        ScType.extractDesignated(selfType) match {
                          case Some((cl: PsiClass, subst)) =>
                            if (cl == clazz) tp
                            else null
                          case _ =>
                            selfType match {
                              case ScCompoundType(types, _, _, _) =>
                                val iter = types.iterator
                                while (iter.hasNext) {
                                  val tps = iter.next()
                                  ScType.extractClass(tps) match {
                                    case Some(cl) => {
                                      if (cl == clazz) return tp
                                    }
                                    case _ =>
                                  }
                                }
                              case _ =>
                            }
                            null
                        }
                      case None => null
                    }
                  }
                case Some((cl: PsiClass, subst)) => {
                  if (cl == clazz) tp
                  else if (ScalaPsiUtil.cachedDeepIsInheritor(cl, clazz)) tp
                  else null
                }
                case Some((named: ScTypedDefinition, subst)) =>
                  update(named.getType(TypingContext.empty).getOrAny)
                case _ =>
                  typez match {
                    case ScCompoundType(types, _, _, _) =>
                      val iter = types.iterator
                      while (iter.hasNext) {
                        val tps = iter.next()
                        ScType.extractClass(tps) match {
                          case Some(cl) => {
                            if (cl == clazz) return tp
                            else if (ScalaPsiUtil.cachedDeepIsInheritor(cl, clazz)) return tp
                          }
                          case _ =>
                        }
                      }
                    case t: ScTypeParameterType => return update(t.upper.v)
                    case p@ScParameterizedType(des, typeArgs) => {
                      p.designator match {
                        case ScTypeParameterType(_, _, _, upper, _) => return update(p.substitutor.subst(upper.v))
                        case _ =>
                      }
                    }
                    case _ =>
                  }
                  null
              }
            }
            while (tp != null) {
              val up = update(tp)
              if (up != null) return up
              tp match {
                case ScProjectionType(newType, _, _, _) => tp = newType
                case _ => tp = null
              }
            }
            t
          }
          case _ => t
        }
      case tpt: ScTypeParameterType => tvMap.get((tpt.name, tpt.getId)) match {
        case None => tpt
        case Some(v) => extractTpt(tpt, v)
      }
      case u: ScUndefinedType => tvMap.get((u.tpt.name, u.tpt.getId)) match {
        case None => u
        case Some(v) => v match {
          case tpt: ScTypeParameterType if tpt.param == u.tpt.param => u
          case _ => extractTpt(u.tpt, v)
        }
      }
      case u: ScAbstractType => tvMap.get((u.tpt.name, u.tpt.getId)) match {
        case None => u
        case Some(v) => v match {
          case tpt: ScTypeParameterType if tpt.param == u.tpt.param => u
          case _ => extractTpt(u.tpt, v)
        }
      }
      case tv: ScTypeVariable => tvMap.get((tv.name, "")) match {
        case None => tv
        case Some(v) => v
      }
      case JavaArrayType(arg) => JavaArrayType(substInternal(arg))
      case pt@ScParameterizedType(tpt: ScTypeParameterType, typeArgs) => {
        tvMap.get((tpt.name, tpt.getId)) match {
          case Some(param: ScParameterizedType) if pt != param => {
            if (tpt.args.length == 0) {
              substInternal(param) //to prevent types like T[A][A]
            } else {
              ScParameterizedType(param.designator, typeArgs.map(substInternal(_)))
            }
          }
          case _ => {
            substInternal(tpt) match {
              case ScParameterizedType(des, _) => new ScParameterizedType(des, typeArgs map {
                substInternal(_)
              })
              case des => new ScParameterizedType(des, typeArgs map {
                substInternal(_)
              })
            }
          }
        }
      }
      case pt@ScParameterizedType(u: ScUndefinedType, typeArgs) => {
        tvMap.get((u.tpt.name, u.tpt.getId)) match {
          case Some(param: ScParameterizedType) if pt != param => {
            if (u.tpt.args.length == 0) {
              substInternal(param) //to prevent types like T[A][A]
            } else {
              ScParameterizedType(param.designator, typeArgs.map(substInternal(_)))
            }
          }
          case _ => {
            substInternal(u) match {
              case ScParameterizedType(des, _) => new ScParameterizedType(des, typeArgs map {
                substInternal(_)
              })
              case des => new ScParameterizedType(des, typeArgs map {
                substInternal(_)
              })
            }
          }
        }
      }
      case pt@ScParameterizedType(u: ScAbstractType, typeArgs) => {
        tvMap.get((u.tpt.name, u.tpt.getId)) match {
          case Some(param: ScParameterizedType) if pt != param => {
            if (u.tpt.args.length == 0) {
              substInternal(param) //to prevent types like T[A][A]
            } else {
              ScParameterizedType(param.designator, typeArgs.map(substInternal(_)))
            }
          }
          case _ => {
            substInternal(u) match {
              case ScParameterizedType(des, _) => new ScParameterizedType(des, typeArgs map {
                substInternal(_)
              })
              case des => new ScParameterizedType(des, typeArgs map {
                substInternal(_)
              })
            }
          }
        }
      }
      case ScParameterizedType(des, typeArgs) => {
        substInternal(des) match {
          case ScParameterizedType(des, _) => new ScParameterizedType(des, typeArgs map {
            substInternal(_)
          })
          case des => new ScParameterizedType(des, typeArgs map {
            substInternal(_)
          })
        }
      }
      case ScExistentialArgument(name, args, lower, upper) =>
        new ScExistentialArgument(name, args, substInternal(lower), substInternal(upper))
      case ex@ScExistentialType(q, wildcards) => {
        //remove bound names
        val trunc = aliasesMap -- ex.boundNames
        new ScExistentialType(new ScSubstitutor(tvMap, trunc, updateThisType, follower).substInternal(q), wildcards)
      }
      case comp@ScCompoundType(comps, decls, typeDecls, substitutor) => {
        ScCompoundType(comps.map(substInternal(_)), decls, typeDecls, substitutor.followed(
          new ScSubstitutor(tvMap, aliasesMap, updateThisType)
        ))
      }
      case _ => t
    }
  }
}

class ScUndefinedSubstitutor(val upperMap: Map[(String, String), Seq[ScType]], val lowerMap: Map[(String, String), Seq[ScType]]) {
  type Name = (String, String)

  def this() = this(HashMap.empty, HashMap.empty)

  def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty

  //todo: this is can be rewritten in more fast way
  def addSubst(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = {
    var res: ScUndefinedSubstitutor = this
    for ((name, seq) <- subst.upperMap) {
      for (upper <- seq) {
        res = res.addUpper(name, upper)
      }
    }
    for ((name, seq) <- subst.lowerMap) {
      for (lower <- seq) {
        res = res.addLower(name, lower)
      }
    }

    res
  }

  def +(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = addSubst(subst)

  def addLower(name: Name, _lower: ScType): ScUndefinedSubstitutor = {
    val lower = _lower.recursiveVarianceUpdate((tp: ScType, i: Int) => {
      tp match {
        case ScAbstractType(_, lower, upper) =>
          i match {
            case -1 => (true, lower)
            case 1 => (true, upper)
            case 0 => (true, ScExistentialArgument("_", Nil, lower, upper))
          }
        case _ => (true, tp)
      }
    }, -1)
    lowerMap.get(name) match {
      case Some(seq: Seq[ScType]) => new ScUndefinedSubstitutor(upperMap, lowerMap.updated(name, Seq(lower) ++ seq))
      case None => new ScUndefinedSubstitutor(upperMap, lowerMap + ((name, Seq(lower))))
    }
  }

  def addUpper(name: Name, _upper: ScType): ScUndefinedSubstitutor = {
    val upper = _upper.recursiveVarianceUpdate((tp: ScType, i: Int) => {
      tp match {
        case ScAbstractType(_, lower, upper) =>
          i match {
            case -1 => (true, lower)
            case 1 => (true, upper)
            case 0 => (true, ScExistentialArgument("_", Nil, lower, upper))
          }
        case _ => (true, tp)
      }
    }, 1)
    upperMap.get(name) match {
      case Some(seq: Seq[ScType]) => new ScUndefinedSubstitutor(upperMap.updated(name, Seq(upper) ++ seq), lowerMap)
      case None => new ScUndefinedSubstitutor(upperMap + ((name, Seq(upper))), lowerMap)
    }
  }

  def getSubstitutor: Option[ScSubstitutor] = getSubstitutor(notNonable = false)

  val names: Set[Name] = {
    upperMap.keySet ++ lowerMap.keySet
  }
  import collection.mutable.{HashMap => MHashMap}
  import collection.immutable.{HashMap => IHashMap}
  val lMap = new MHashMap[Name, ScType]
  val rMap = new MHashMap[Name, ScType]

  def getSubstitutor(notNonable: Boolean): Option[ScSubstitutor] = {
    import collection.immutable.HashSet
    val tvMap = new MHashMap[Name, ScType]

    def solve(name: Name, visited: HashSet[Name]): Option[ScType] = {
      if (visited.contains(name)) {
        tvMap += ((name, Nothing))
        return None
      }
      tvMap.get(name) match {
        case Some(tp) => Some(tp)
        case _ =>
          lowerMap.get(name) match {
            case Some(seq) =>
              var res = false
              def checkRecursive(tp: ScType): Boolean = {
                tp.recursiveUpdate {
                  case tpt: ScTypeParameterType =>
                    val otherName = (tpt.name, tpt.getId)
                    if (names.contains(otherName)) {
                        res = true
                        solve(otherName, visited + name) match {
                          case None if !notNonable => return false
                          case _ =>
                        }
                    }
                    (false, tpt)
                  case ScUndefinedType(tpt) =>
                    val otherName = (tpt.name, tpt.getId)
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
              val seqIterator = seq.iterator
              while (seqIterator.hasNext) {
                val p = seqIterator.next()
                if (!checkRecursive(p)) {
                  tvMap += ((name, Nothing))
                  return None
                }
              }
              if (seq.nonEmpty) {
                val subst = if (res) new ScSubstitutor(IHashMap.empty ++ tvMap, Map.empty, None) else ScSubstitutor.empty
                var lower = subst.subst(seq(0))
                var i = 1
                while (i < seq.length) {
                  lower = Bounds.lub(lower, seq(i))
                  i += 1
                }
                lMap += ((name, lower))
                tvMap += ((name, lower))
              }
            case None =>
          }
          upperMap.get(name) match {
            case Some(seq) =>
              var res = false
              def checkRecursive(tp: ScType): Boolean = {
                tp.recursiveUpdate {
                  case tpt: ScTypeParameterType =>
                    val otherName = (tpt.name, tpt.getId)
                    if (names.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case ScUndefinedType(tpt) =>
                    val otherName = (tpt.name, tpt.getId)
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
              val seqIterator = seq.iterator
              while (seqIterator.hasNext) {
                val p = seqIterator.next()
                if (!checkRecursive(p)) {
                  tvMap += ((name, Nothing))
                  return None
                }
              }
              if (seq.nonEmpty) {
                var rType: ScType = Nothing
                val subst = if (res) new ScSubstitutor(IHashMap.empty ++ tvMap, Map.empty, None) else ScSubstitutor.empty
                if (seq.length == 1) {
                  rType = subst.subst(seq(0))
                  rMap += ((name, rType))
                } else if (seq.length > 1) {
                  rType = Bounds.glb(seq.map(subst.subst(_)), checkWeak = false)
                  rMap += ((name, rType))
                }
                tvMap.get(name) match {
                  case Some(lower) =>
                    if (!notNonable) {
                      val seqIterator = seq.iterator
                      while (seqIterator.hasNext) {
                        val upper = seqIterator.next()
                        if (!lower.conforms(subst.subst(upper))) {
                          return None
                        }
                      }
                    }
                  case None => tvMap += ((name, rType))
                }
              }
            case None =>
          }

          if (tvMap.get(name) == None) {
            tvMap += ((name, Nothing))
          }
          tvMap.get(name)
      }
    }
    val namesIterator = names.iterator
    while (namesIterator.hasNext) {
      val name = namesIterator.next()
      solve(name, HashSet.empty) match {
        case Some(tp) => // do nothing
        case None if !notNonable => return None
        case _ =>
      }
    }
    val subst = new ScSubstitutor(IHashMap.empty ++ tvMap, Map.empty, None)
    Some(subst)
  }
}