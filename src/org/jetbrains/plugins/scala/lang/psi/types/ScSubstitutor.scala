/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types


import api.base.ScReferenceElement
import com.intellij.psi.{PsiTypeParameterListOwner, PsiTypeParameter, PsiSubstitutor, PsiClass}
import com.intellij.openapi.util.Key
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.ScTypeAlias

object ScSubstitutor {
  val empty = new ScSubstitutor {
    override def subst(t: ScType): ScType = t
    override def followed(s : ScSubstitutor) : ScSubstitutor = s
  }

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class ScSubstitutor(val tvMap: Map[String, ScType],
                    val aliasesMap: Map[String, Suspension[ScType]],
                    val outerMap: Map[PsiClass, Tuple2[ScType, ScReferenceElement]]) {
  def this() = this (Map.empty, Map.empty, Map.empty)

  def bindT(name : String, t: ScType) = new ScSubstitutor(tvMap + ((name, t)), aliasesMap, outerMap)
  def bindA(name: String, t: ScType) = new ScSubstitutor(tvMap, aliasesMap + ((name, new Suspension[ScType](t))), outerMap)
  def bindA(name: String, f: () => ScType) = new ScSubstitutor(tvMap, aliasesMap + ((name, new Suspension[ScType](f))), outerMap)
  def bindO(outer: PsiClass, t: ScType, ref : ScReferenceElement) = new ScSubstitutor(tvMap, aliasesMap, outerMap + ((outer, (t, ref))))
  def incl(s: ScSubstitutor) = new ScSubstitutor(s.tvMap ++ tvMap, s.aliasesMap ++ aliasesMap, s.outerMap ++ outerMap)
  def followed(s: ScSubstitutor) : ScSubstitutor = new ScSubstitutor(tvMap, aliasesMap, outerMap) {
    override def subst(t: ScType) = {
      s.subst(ScSubstitutor.this.subst(t))
    }
  }

  def subst(t: ScType) : ScType = t match {
    case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), Seq(params map (subst _): _*))
    case ScTupleType(comps) => new ScTupleType(comps map {subst _})
    case ScProjectionType(proj, ref) => new ScProjectionType(subst(proj), ref)

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
        new ScTypeAliasType(alias, args, () => subst(lower.v), () => subst(upper.v))
      }
    }
    case ScParameterizedType (des, typeArgs) => {
      val args = typeArgs map {subst _}
      subst(des) match {
        case ScTypeConstructorType(_, tcArgs, aliased) => {
          val s1 = args.zip(tcArgs.toArray).foldLeft(ScSubstitutor.empty) {(s, p) => s bindT (p._2.name, p._1)}
          s1.subst(aliased.v)
        }
        case des => new ScParameterizedType(des, Seq(args : _*))
      }
    }
    case ScExistentialArgument(name, args, lower, upper) =>
      new ScExistentialArgument(name, args, subst(lower), subst(upper))
    case ex@ScExistentialType(q, wildcards) => {
      //remove bound names
      val trunc = aliasesMap -- ex.boundNames
      new ScExistentialType(new ScSubstitutor(tvMap, trunc, outerMap).subst(q), wildcards)
    }
    case _ => t
  }
}