/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam

class ScSubstitutor {
  private var map : Map[ScTypeParam, ScType] = Map.empty[ScTypeParam, ScType]
  
  def this(_map :  Map[ScTypeParam, ScType]) = {
    this()
    map = _map
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
      case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map ((p : Tuple2[ScType, Boolean]) => (subst(p._1), p._2)))
      case _ => t //todo
    }
  }
}