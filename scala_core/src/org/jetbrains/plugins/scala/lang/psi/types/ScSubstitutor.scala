/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiTypeParameter, PsiSubstitutor}
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.params.ScParameter
import api.statements.ScTypeAlias

object ScSubstitutor {
  val empty = new ScSubstitutor

  val key : Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class ScSubstitutor(val map : Map[PsiTypeParameter, ScType], val aliasesMap : Map[String, ScType]) {

  def this() = this(Map.empty, Map.empty)

  def +(p : PsiTypeParameter, t : ScType) = new ScSubstitutor(map + ((p, t)), aliasesMap)
  def +(p : ScTypeAlias, t : ScType) = new ScSubstitutor(map, aliasesMap + ((p.name, t)))

  def subst(p : PsiTypeParameter) = map.get(p) match {
    case None => new ScDesignatorType(p)
    case Some(v) => v
  }

  def subst(alias : ScTypeAlias) = aliasesMap.get(alias.name) match {
    case None => new ScDesignatorType(alias)
    case Some(v) => v
  }

  def subst (t : ScType) : ScType = {
    t match {
      case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map (subst _))
      case ScTupleType(comps) => new ScTupleType(comps map {subst _})
      case ScDesignatorType(e) => e match {
        case tp : PsiTypeParameter => subst(tp)
        case alias : ScTypeAlias => subst(alias)
        case _ => t
      }
      case ScParameterizedType (des, typeArgs) =>
        new ScParameterizedType(des, typeArgs map {subst _})
      case ex@ScExistentialType(q, decls) => {
        //remove bound names 
        val trunc = aliasesMap.excl(ex.boundNames)
        new ScExistentialType(new ScSubstitutor(map, trunc).subst(q), decls)
      }
      case _ => t //todo
    }
  }
}