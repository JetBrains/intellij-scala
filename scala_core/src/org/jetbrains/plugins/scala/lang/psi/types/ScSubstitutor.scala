/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiTypeParameter

object ScSubstitutor {
  val empty = new ScSubstitutor

  val key : Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class ScSubstitutor(val map : Map[PsiTypeParameter, ScType]) {

  def this() = {
    this(Map.empty)
  }

  def put(p : PsiTypeParameter, t : ScType) = new ScSubstitutor(map + ((p, t)))

  def subst(p : PsiTypeParameter) = {
    map.get(p) match {
      case None => null //todo return type of type parameter itself
      case Some(v) => v
    }
  }

  def subst (t : ScType) : ScType = {
    t match {
      case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map (t => subst(t)))
      case ScParameterizedType (td, s) => td match {
        case tp : PsiTypeParameter => subst(tp)
        case _ => {
          val newMap = map transform ((tp : PsiTypeParameter, t : ScType) => subst(s.subst(t)))
          new ScParameterizedType(td, new ScSubstitutor(newMap))
        }
      }
      case _ => t //todo
    }
  }
}

class Signature(val types : Seq[ScType], val substitutor : ScSubstitutor) {
  def eq(other : Signature) : Boolean = {
    types.equalsWith(other.types) {(t1, t2) => substitutor.subst(t1) equiv other.substitutor.subst(t2)}
  }
}