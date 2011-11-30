package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.util.Key
import collection.immutable.{Map, HashMap}
import java.lang.String
import nonvalue.{Parameter, TypeParameter, ScTypePolymorphicType, ScMethodType}
import com.intellij.psi._
import api.toplevel.ScTypedDefinition
import result.TypingContext

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
      case ScProjectionType(proj, element, subst) => new ScProjectionType(substInternal(proj), element, subst)
      case m@ScMethodType(retType, params, isImplicit) => new ScMethodType(substInternal(retType),
        params.map(p => p.copy(paramType = substInternal(p.paramType), expectedType = substInternal(p.expectedType))), isImplicit)(m.project, m.scope)
      case ScTypePolymorphicType(internalType, typeParameters) => {
        ScTypePolymorphicType(substInternal(internalType), typeParameters.map(tp => {
          TypeParameter(tp.name, substInternal(tp.lowerType), substInternal(tp.upperType), tp.ptp)
        }))
      }
      case ScThisType(clazz) => {
        updateThisType match {
          case Some(oldTp) => {
            var tp = oldTp
            def update(typez: ScType): ScType = {
              ScType.extractDesignated(typez) match {
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
                    case _ =>
                  }
                  null
              }
            }
            while (tp != null) {
              val up = update(tp)
              if (up != null) return up
              tp match {
                case ScProjectionType(newType, _, _) => tp = newType
                case _ => tp = null
              }
            }
            t
          }
          case _ => t
        }
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

class ScUndefinedSubstitutor(val upperMap: Map[(String, String), Seq[ScType]], val lowerMap: Map[(String, String), ScType]) {
  def this() = this(HashMap.empty, HashMap.empty)

  //todo: this is can be rewritten in more fast way
  def addSubst(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = {
    var res: ScUndefinedSubstitutor = this
    for ((name, seq) <- subst.upperMap) {
      for (upper <- seq) {
        res = res.addUpper(name, upper)
      }
    }
    for ((name, lower) <- subst.lowerMap) {
      res = res.addLower(name, lower)
    }

    res
  }

  def +(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = addSubst(subst)

  def addLower(name: (String, String), _lower: ScType): ScUndefinedSubstitutor = {
    val lower = _lower.recursiveVarianceUpdate((tp: ScType, i: Int) => {
      tp match {
        case ScAbstractType(_, lower, upper) =>
          i match {
            case -1 => (false, lower)
            case 1 => (false, upper)
            case 0 => (false, ScExistentialArgument("_", Nil, lower, upper))
          }
        case _ => (true, tp)
      }
    }, -1)
    lowerMap.get(name) match {
      case Some(tp: ScType) => new ScUndefinedSubstitutor(upperMap, lowerMap.updated(name, Bounds.lub(lower, tp)))
      case None => new ScUndefinedSubstitutor(upperMap, lowerMap + Tuple(name, lower))
    }
  }

  def addUpper(name: (String, String), _upper: ScType): ScUndefinedSubstitutor = {
    val upper = _upper.recursiveVarianceUpdate((tp: ScType, i: Int) => {
      tp match {
        case ScAbstractType(_, lower, upper) =>
          i match {
            case -1 => (false, lower)
            case 1 => (false, upper)
            case 0 => (false, ScExistentialArgument("_", Nil, lower, upper))
          }
        case _ => (true, tp)
      }
    }, 1)
    upperMap.get(name) match {
      case Some(seq: Seq[ScType]) => new ScUndefinedSubstitutor(upperMap.updated(name, Seq(upper) ++ seq), lowerMap)
      case None => new ScUndefinedSubstitutor(upperMap + Tuple(name, Seq(upper)), lowerMap)
    }
  }
  
  def getSubstitutor: Option[ScSubstitutor] = getSubstitutor(false)

  def getSubstitutor(notNonable: Boolean): Option[ScSubstitutor] = {
    import collection.mutable.HashMap
    val tvMap = new HashMap[(String, String), ScType]
    for (tuple <- lowerMap) {
      tvMap += tuple
    }
    var break = false
    for ((name, seq) <- upperMap if !break) {
      tvMap.get(name) match {
        case Some(lower: ScType) => {
          if (!notNonable)
            for (upper <- seq if !break) {
              if (!lower.conforms(upper)) break = true
            }
        }
        case None if seq.length > 1 => tvMap += Tuple(name, Bounds.glb(seq, false))
        case None if seq.length == 0 => tvMap += Tuple(name, Nothing)
        case None => tvMap += Tuple(name, seq(0))
      }
    }
    if (break) return None
    val map = collection.immutable.HashMap.empty[(String, String), ScType] ++ tvMap
    //val subst = new ScSubstitutor(map, collection.immutable.HashMap.empty, collection.immutable.HashMap.empty)
    val subst = map.toSeq.foldLeft(ScSubstitutor.empty)((a, b) => a.bindT(b._1, b._2))
    Some(subst.followed(subst).followed(subst))
  }
}

object ScUndefinedSubstitutor {
  def removeUndefindes(tp: ScType): ScType = tp match {
     case f@ScFunctionType(ret, params) => new ScFunctionType(removeUndefindes(ret),
      collection.immutable.Seq(params.map(removeUndefindes _).toSeq : _*))(f.getProject, f.getScope)
    case t1@ScTupleType(comps) => new ScTupleType(comps map {removeUndefindes _})(t1.getProject, t1.getScope)
    case ScProjectionType(proj, element, subst) => new ScProjectionType(removeUndefindes(proj), element, subst)
    case tpt : ScTypeParameterType => tpt
    case u: ScUndefinedType => u.tpt
    case tv : ScTypeVariable => tv
    case ScDesignatorType(e) => tp
    case JavaArrayType(arg) => JavaArrayType(removeUndefindes(arg))
    case ScParameterizedType (des, typeArgs) => ScParameterizedType(removeUndefindes(des),
      collection.immutable.Seq(typeArgs.map(removeUndefindes _).toSeq: _*))
    case ScExistentialArgument(name, args, lower, upper) =>
      new ScExistentialArgument(name, args, removeUndefindes(lower), removeUndefindes(upper))
    case ex@ScExistentialType(q, wildcards) => {
      new ScExistentialType(removeUndefindes(q), wildcards)
    }
    case _ => tp
  }
}