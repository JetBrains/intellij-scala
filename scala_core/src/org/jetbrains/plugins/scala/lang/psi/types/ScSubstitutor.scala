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

class ScSubstitutor(val tvMap: Map[ScTypeVariable, ScType],
                    val outerMap: Map[PsiClass, Tuple2[ScType, ScReferenceElement]],
                    val aliasesMap: Map[String, Suspension[ScType]]) {

  def this() = this (Map.empty, Map.empty, Map.empty)

  def +(p: ScTypeVariable, t: ScType) = new ScSubstitutor(tvMap + ((p, t)), outerMap, aliasesMap)
  def +(name: String, t: ScType) = new ScSubstitutor(tvMap, outerMap, aliasesMap + ((name, new Suspension[ScType](t))))
  def +(name: String, f: () => ScType) = new ScSubstitutor(tvMap, outerMap, aliasesMap + ((name, new Suspension[ScType](f))))
  def bindOuter(outer: PsiClass, t: ScType, ref : ScReferenceElement) = new ScSubstitutor(tvMap, outerMap + ((outer, (t, ref))), aliasesMap)
  def incl(s: ScSubstitutor) = new ScSubstitutor(s.tvMap ++ tvMap, s.outerMap ++ outerMap, s.aliasesMap ++ aliasesMap)
  def followed(s: ScSubstitutor) : ScSubstitutor = new ScSubstitutor(tvMap, outerMap, aliasesMap) {
    override def subst(t: ScType) = s.subst(super.subst(t))
  }

  def subst(t: ScType) : ScType = t match {
    case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map (subst _))
    case ScTupleType(comps) => new ScTupleType(comps map {subst _})
    case ScProjectionType(proj, ref) => new ScProjectionType(subst(proj), ref)

    case tv : ScTypeVariable => tvMap.get(tv) match {
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
    case ScTypeAliasType(name, args, lower, upper) => aliasesMap.get(name) match {
      case Some(v) => v.t
      case None => new ScTypeAliasType(name, args, subst(lower), subst(upper))
    }
    case ScParameterizedType (des, typeArgs) =>
      new ScParameterizedType(des, typeArgs map {subst _})
    case ScExistentialArgument(name, args, lower, upper) =>
      new ScExistentialArgument(name, args, subst(lower), subst(upper))
    case ex@ScExistentialType(q, wildcards) => {
      //remove bound names
      val trunc = aliasesMap.excl(ex.boundNames)
      new ScExistentialType(new ScSubstitutor(tvMap, outerMap, trunc).subst(q), wildcards)
    }
    case _ => t
  }
}