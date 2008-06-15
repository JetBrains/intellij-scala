/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiTypeParameter, PsiSubstitutor}
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

object ScSubstitutor {
  val empty = new ScSubstitutor

  val key : Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class ScSubstitutor(val map : Map[PsiTypeParameter, ScType]) {

  def this() = {
    this(Map.empty)
  }

  def +(p : PsiTypeParameter, t : ScType) = new ScSubstitutor(map + ((p, t)))

  def subst(p : PsiTypeParameter) = {
    map.get(p) match {
      case None => new ScDesignatorType(p)
      case Some(v) => v
    }
  }

  def subst (t : ScType) : ScType = {
    t match {
      case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map (subst _))
      case ScTupleType(comps) => new ScTupleType(comps map {subst _})
      case ScDesignatorType(e) => e match {
        case tp : PsiTypeParameter => subst(tp)
        case _ => t
      }
      case ScParameterizedType (des, typeArgs) =>
        new ScParameterizedType(des, typeArgs map {subst _})
      case _ => t //todo
    }
  }
}