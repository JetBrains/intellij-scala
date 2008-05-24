/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import com.intellij.openapi.util.Key

object ScSubstitutor {
  val empty = new ScSubstitutor

  val key : Key[ScSubstitutor] = Key.create("scala substitutor key")
}

class ScSubstitutor(val map : Map[ScTypeParam, ScType]) {

  def this() = {
    this(Map.empty)
  }

  def put(p : ScTypeParam, t : ScType) = new ScSubstitutor(map + ((p, t)))

  def subst(p : ScTypeParam) = {
    map.get(p) match {
      case None => null //todo return type of type parameter itself
      case Some(v) => v
    }
  }

  def subst (t : ScType) : ScType = {
    t match {
      case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map (t => subst(t)))
      case ScParameterizedType (td, s) => td match {
        case tp : ScTypeParam => subst(tp)
        case _ => {
          val newMap = map transform ((tp : ScTypeParam, t : ScType) => subst(s.subst(t)))
          new ScParameterizedType(td, new ScSubstitutor(newMap))
        }
      }
      case _ => t //todo
    }
  }
}