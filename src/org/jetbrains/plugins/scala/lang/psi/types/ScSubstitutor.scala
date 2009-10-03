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
import org.jetbrains.annotations.NotNull

object ScSubstitutor {
  val empty = new ScSubstitutor()

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class
ScSubstitutor(val tvMap: Map[String, ScType],
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

  def subst(t: ScType): ScType = if (follower != null) follower.subst(substInternal(t)) else substInternal(t)

  protected def substInternal(t: ScType) : ScType = t match {
    case ScFunctionType(ret, params) => new ScFunctionType(substInternal(ret), collection.immutable.Sequence(params.map(substInternal _).toSeq : _*))
    case ScTupleType(comps) => new ScTupleType(comps map {substInternal _})
    case ScProjectionType(proj, ref) => new ScProjectionType(substInternal(proj), ref)

    case tpt : ScTypeParameterType => tvMap.get(tpt.name) match {
      case None => tpt
      case Some(v) => v
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
    case ScParameterizedType (des, typeArgs) => {
      val args = typeArgs map {substInternal _}
      substInternal(des) match {
        case ScTypeConstructorType(_, tcArgs, aliased) => {
          val s1 = args.zip(tcArgs.toSeq).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._2.name, p._1)}
          s1.subst(aliased.v)
        }
        case des => new ScParameterizedType(des, collection.immutable.Sequence(args.toSeq : _*))
      }
    }
    case ScExistentialArgument(name, args, lower, upper) =>
      new ScExistentialArgument(name, args, substInternal(lower), substInternal(upper))
    case ex@ScExistentialType(q, wildcards) => {
      //remove bound names
      val trunc = aliasesMap -- ex.boundNames
      new ScExistentialType(new ScSubstitutor(tvMap, trunc, outerMap, follower).substInternal(q), wildcards)
    }
    case _ => t
  }
}