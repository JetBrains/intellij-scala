/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.{PsiTypeParameterListOwner, PsiTypeParameter, PsiSubstitutor, PsiClass}
import com.intellij.openapi.util.Key
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.ScTypeAlias

object ScSubstitutor {
  val empty = new ScSubstitutor {
    override def subst(t: ScType): ScType = t
  }

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class ScSubstitutor(val tpMap: Map[PsiTypeParameter, ScType],
                   val outerMap: Map[PsiClass, ScType],
                   val aliasesMap: Map[String, ScType]) {

  def this() = this (Map.empty, Map.empty, Map.empty)

  def +(p: PsiTypeParameter, t: ScType) = new ScSubstitutor(tpMap + ((p, t)), outerMap, aliasesMap)
  def +(name: String, t: ScType) = new ScSubstitutor(tpMap, outerMap, aliasesMap + ((name, t)))
  def bindOuter(outer: PsiClass, t: ScType) = new ScSubstitutor(tpMap, outerMap + ((outer, t)), aliasesMap)
  def incl(s: ScSubstitutor) = new ScSubstitutor(s.tpMap ++ tpMap, s.outerMap ++ outerMap, s.aliasesMap ++ aliasesMap)
  def followed(s: ScSubstitutor) = new ScSubstitutor(tpMap, outerMap, aliasesMap) {
    override def subst(t: ScType) = s.subst(super.subst(t))
    override def subst(ta: ScTypeAlias) = s.subst(super.subst(ta))
    override def subst(tp: PsiTypeParameter) = s.subst(super.subst(tp))
  }

  def subst(p: PsiTypeParameter) = tpMap.get(p) match {
    case None => new ScDesignatorType(p)
    case Some(v) => v
  }

  def subst(ta: ScTypeAlias) = aliasesMap.get(ta.name) match {
    case Some(v) => v
    case None => new ScPolymorphicType(ta, ScSubstitutor.empty)
  }

  def subst(t: ScType) : ScType = {
    t match {
      case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map (subst _))
      case ScTupleType(comps) => new ScTupleType(comps map {
        subst _
      })
      case ScDesignatorType(e) if (!e.isInstanceOf[ScTypeAlias]) => e match { //scala ticket 425
        case tp: PsiTypeParameter => subst(tp)
        case c: PsiClass => {
          val cc = c.getContainingClass
          if (cc != null) {
            outerMap.get(cc) match {
              case Some(ot) => new ScProjectionType(ot, c.getName)
              case None => t
            }
          } else t
        }
        case _ => t
      }
      case ScPolymorphicType(a, s) => aliasesMap.get(a.name) match {
        case Some(v) => v
        case None => new ScPolymorphicType(a, s.incl(this))
      }
      case ScParameterizedType (des, typeArgs) =>
        new ScParameterizedType(des, typeArgs map {subst _})
      case ScExistentialArgument(lower, upper) => new ScExistentialArgument(subst(lower), subst(upper))
      case ex@ScExistentialType(q, wildcards) => {
        //remove bound names 
        val trunc = aliasesMap.excl(ex.boundNames)
        new ScExistentialType(new ScSubstitutor(tpMap, outerMap, trunc).subst(q), wildcards)
      }
      case _ => t //todo
    }
  }
}