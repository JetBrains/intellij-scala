/**
* @author ven
*/
package org.jetbrains.plugins.scala
package lang
package psi
package types


import api.base.ScReferenceElement
import com.intellij.psi.{PsiTypeParameterListOwner, PsiTypeParameter, PsiSubstitutor, PsiClass}
import com.intellij.openapi.util.Key
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.ScTypeAlias
import java.lang.String
import nonvalue.{Parameter, TypeParameter, ScTypePolymorphicType, ScMethodType}
import org.jetbrains.annotations.NotNull
import types.ScCompoundType

object ScSubstitutor {
  val empty = new ScSubstitutor()

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class ScSubstitutor(val tvMap: Map[String, ScType],
                    val aliasesMap: Map[String, Suspension[ScType]],
                    val outerMap: Map[PsiClass, Tuple2[ScType, ScReferenceElement]]) {
  def this() = this (Map.empty, Map.empty, Map.empty)

  def this(tvMap: Map[String, ScType],
                    aliasesMap: Map[String, Suspension[ScType]],
                    outerMap: Map[PsiClass, Tuple2[ScType, ScReferenceElement]], follower: ScSubstitutor) = {
    this(tvMap, aliasesMap, outerMap)
    this.follower = follower
  }

  private var follower: ScSubstitutor = null

  override def toString: String = "ScSubstitutor(" + tvMap + ", " + aliasesMap + ", " + outerMap + ")" +
    (if (follower != null) " followed " + follower.toString else "")

  def bindT(name : String, t: ScType) = new ScSubstitutor(tvMap + ((name, t)), aliasesMap, outerMap, follower)
  def bindA(name: String, t: ScType) = new ScSubstitutor(tvMap, aliasesMap + ((name, new Suspension[ScType](t))), outerMap, follower)
  def bindA(name: String, f: () => ScType) = new ScSubstitutor(tvMap, aliasesMap + ((name, new Suspension[ScType](f))), outerMap, follower)
  def bindO(outer: PsiClass, t: ScType, ref : ScReferenceElement) = new ScSubstitutor(tvMap, aliasesMap, outerMap + ((outer, (t, ref))), follower)
  def incl(s: ScSubstitutor) = new ScSubstitutor(s.tvMap ++ tvMap, s.aliasesMap ++ aliasesMap, s.outerMap ++ outerMap, follower)
  def followed(s: ScSubstitutor) : ScSubstitutor = new ScSubstitutor(tvMap, aliasesMap, outerMap,
    if (follower != null) follower followed s else s)

  def subst(t: ScType): ScType = try {
    if (follower != null) follower.subst(substInternal(t)) else substInternal(t)
  }
  catch {
    case s: StackOverflowError => throw new RuntimeException("StackOverFlow during ScSubstitutor.subst(" + t + ") this = " + this, s)
  }

  protected def substInternal(t: ScType) : ScType = t match {
    case f@ScFunctionType(ret, params) => new ScFunctionType(substInternal(ret), 
      collection.immutable.Seq(params.map(substInternal _).toSeq : _*), f.isImplicit)
    case t1@ScTupleType(comps) => new ScTupleType(comps map {substInternal _}, t1.getProject)
    case ScProjectionType(proj, ref) => new ScProjectionType(substInternal(proj), ref)
    case ScMethodType(retType, params, isImplicit) => ScMethodType(substInternal(retType), params.map(p => {
      Parameter(p.name, substInternal(p.paramType), p.isDefault, p.isRepeated)
    }), isImplicit)
    case ScTypePolymorphicType(internalType, typeParameters) => {
      ScTypePolymorphicType(substInternal(internalType), typeParameters.map(tp => {
        TypeParameter(tp.name, substInternal(tp.lowerType), substInternal(tp.upperType), tp.ptp)
      }))
    }
    case tpt : ScTypeParameterType => tvMap.get(tpt.name) match {
      case None => tpt
      case Some(v) => v
    }
    case u: ScUndefinedType => tvMap.get(u.tpt.name) match {
      case None => u
      case Some(v) => v match {
        case tpt: ScTypeParameterType if tpt.param == u.tpt.param => u
        case _ => v
      }
    }
    case tv : ScTypeVariable => tvMap.get(tv.name) match {
      case None => tv
      case Some(v) => v
    }
    case ScDesignatorType(e) => e match {
      case c: PsiClass => {
        val cc = c.getContainingClass
        if (cc != null) {
          outerMap.get(cc) match {
            case Some(p) => new ScProjectionType(p._1, p._2)
            case None => t
          }
        } else t
      }
      case _ => t
    }
    case ScTypeAliasType(alias, args, lower, upper) => aliasesMap.get(alias.name) match {
      case Some(t) => t.v
      case None => {
        import Misc.fun2suspension
        new ScTypeAliasType(alias, args, () => substInternal(lower.v), () => substInternal(upper.v))
      }
    }
    case pt@ScParameterizedType(tpt: ScTypeParameterType, typeArgs) => {
      tvMap.get(tpt.name) match {
        case Some(param: ScParameterizedType) if pt != param => substInternal(param) //to prevent types like T[A][A]
        case _ => {
          val args = typeArgs map {substInternal _}
          substInternal(tpt) match {
            case ScTypeConstructorType(_, tcArgs, aliased) => {
              val s1 = args.zip(tcArgs.toSeq).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._2.name, p._1)}
              s1.subst(aliased.v)
            }
            case des => new ScParameterizedType(des, collection.immutable.Seq(args.toSeq : _*))
          }
        }
      }
    }
    case pt@ScParameterizedType(u: ScUndefinedType, typeArgs) => {
      tvMap.get(u.tpt.name) match {
        case Some(param: ScParameterizedType) if pt != param => substInternal(param) //to prevent types like T[A][A]
        case _ => {
          val args = typeArgs map {substInternal _}
          substInternal(u) match {
            case ScTypeConstructorType(_, tcArgs, aliased) => {
              val s1 = args.zip(tcArgs.toSeq).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._2.name, p._1)}
              s1.subst(aliased.v)
            }
            case des => new ScParameterizedType(des, collection.immutable.Seq(args.toSeq : _*))
          }
        }
      }
    }
    case ScParameterizedType (des, typeArgs) => {
      val args = typeArgs map {substInternal _}
      substInternal(des) match {
        case ScTypeConstructorType(_, tcArgs, aliased) => {
          val s1 = args.zip(tcArgs.toSeq).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._2.name, p._1)}
          s1.subst(aliased.v)
        }
        case des => new ScParameterizedType(des, collection.immutable.Seq(args.toSeq : _*))
      }
    }
    case ScExistentialArgument(name, args, lower, upper) =>
      new ScExistentialArgument(name, args, substInternal(lower), substInternal(upper))
    case ex@ScExistentialType(q, wildcards) => {
      //remove bound names
      val trunc = aliasesMap -- ex.boundNames
      new ScExistentialType(new ScSubstitutor(tvMap, trunc, outerMap, follower).substInternal(q), wildcards)
    }
    case comp@ScCompoundType(comps, decls, typeDecls, substitutor) => {
      ScCompoundType(comps.map(substInternal(_)), decls, typeDecls, substitutor.followed(
        new ScSubstitutor(tvMap, aliasesMap, outerMap)
        ))
    }
    case _ => t
  }

  def removeUndefines(tps: Array[PsiTypeParameter]): ScSubstitutor = {
    new ScSubstitutor(tvMap.filter(t => {
      t._2 match {
        case ScUndefinedType(tpt) if tps.contains(tpt.param) => false
        case _ => true
      }
    }), aliasesMap, outerMap, if (follower != null) follower.removeUndefines(tps) else null)
  }
}

class ScUndefinedSubstitutor(val upperMap: Map[String, Seq[ScType]], val lowerMap: Map[String, ScType]) {
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

    return res
  }

  def +(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = addSubst(subst)

  def addLower(name: String, lower: ScType): ScUndefinedSubstitutor = {
    lowerMap.get(name) match {
      case Some(tp: ScType) => new ScUndefinedSubstitutor(upperMap, lowerMap.update(name, Bounds.lub(lower, tp)))
      case None => new ScUndefinedSubstitutor(upperMap, lowerMap + Tuple(name, lower))
    }
  }

  def addUpper(name: String, upper: ScType): ScUndefinedSubstitutor = {
    upperMap.get(name) match {
      case Some(seq: Seq[ScType]) => new ScUndefinedSubstitutor(upperMap.update(name, Seq(upper) ++ seq), lowerMap)
      case None => new ScUndefinedSubstitutor(upperMap + Tuple(name, Seq(upper)), lowerMap)
    }
  }
  
  def getSubstitutor: Option[ScSubstitutor] = {
    import collection.mutable.HashMap
    val tvMap = new HashMap[String, ScType]
    for (tuple <- lowerMap) {
      tvMap += tuple
    }
    for ((name, seq) <- upperMap) {
      tvMap.get(name) match {
        case Some(lower: ScType) => {
          for (upper <- seq) {
            if (!lower.conforms(upper)) return None
          }
        }
        case None if seq.length != 1 => tvMap += Tuple(name, Nothing)
        case None => tvMap += Tuple(name, seq(0))
      }
    }
    val subst = new ScSubstitutor(collection.immutable.HashMap.empty[String, ScType] ++ tvMap,
      collection.immutable.HashMap.empty, collection.immutable.HashMap.empty)
    Some(subst.followed(subst).followed(subst))
  }
}

object ScUndefinedSubstitutor {
  def removeUndefindes(tp: ScType): ScType = tp match {
     case f@ScFunctionType(ret, params) => new ScFunctionType(removeUndefindes(ret),
      collection.immutable.Seq(params.map(removeUndefindes _).toSeq : _*), f.isImplicit)
    case t1@ScTupleType(comps) => new ScTupleType(comps map {removeUndefindes _}, t1.getProject)
    case ScProjectionType(proj, ref) => new ScProjectionType(removeUndefindes(proj), ref)
    case tpt : ScTypeParameterType => tpt
    case u: ScUndefinedType => u.tpt
    case tv : ScTypeVariable => tv
    case ScDesignatorType(e) => tp
    case ScTypeAliasType(alias, args, lower, upper) => {
      import Misc.fun2suspension
      new ScTypeAliasType(alias, args, () => removeUndefindes(lower.v), () => removeUndefindes(upper.v))
    }
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