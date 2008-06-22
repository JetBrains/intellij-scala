/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiTypeParameter, PsiSubstitutor}
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.params.ScParameter
import api.statements.{ScTypeAlias, ScTypeAliasDefinition}

object ScSubstitutor {
  val empty = new ScSubstitutor

  val key : Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class ScSubstitutor(val map : Map[PsiTypeParameter, ScType], val aliasesMap : Map[String, ScType]) {

  def this() = this(Map.empty, Map.empty)

  def +(p : PsiTypeParameter, t : ScType) = new ScSubstitutor(map + ((p, t)), aliasesMap)
  def +(name : String, t : ScType) = new ScSubstitutor(map, aliasesMap + ((name, t)))
  def incl(s : ScSubstitutor) = new ScSubstitutor(s.map ++ map, s.aliasesMap ++ aliasesMap)

  def subst(p : PsiTypeParameter) = map.get(p) match {
    case None => new ScDesignatorType(p)
    case Some(v) => v
  }

  def subst (ta : ScTypeAlias) = aliasesMap.get(ta.name) match {
    case Some(v) => v
    case None => new ScTypeAliasDesignatorType(ta, ScSubstitutor.empty)
  }

  def subst (t : ScType) : ScType = {
    t match {
      case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map (subst _))
      case ScTupleType(comps) => new ScTupleType(comps map {subst _})
      case ScDesignatorType(e) if (!e.isInstanceOf[ScTypeAlias]) => e match { //scala ticket 425
        case tp : PsiTypeParameter => subst(tp)
        case _ => t
      }
      case ScTypeAliasDesignatorType(a, s) => aliasesMap.get(a.name) match {
        case Some(v) => v
        case None => new ScTypeAliasDesignatorType(a, s.incl(this))
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