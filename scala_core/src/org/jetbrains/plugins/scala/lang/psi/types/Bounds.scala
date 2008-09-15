package org.jetbrains.plugins.scala.lang.psi.types

object Bounds {

  def glb(t1: ScType, t2: ScType) = {
    if (t1.conforms(t2)) t1
    else if (t2.conforms(t1)) t2
    else new ScCompoundType(Array(t1, t2), Seq.empty, Seq.empty)
  }

  def lub(t1: ScType, t2: ScType): ScType = {
    if (t1.conforms(t2)) t2
    else if (t2.conforms(t1)) t1
    else (t1, t2) match {
      //Function types
      case (ScFunctionType(rt1, params1), ScFunctionType(rt2, params2))
        if params1.length == params2.length => {
        val px = for ((p1, p2) <- params1.toList.zip(params2.toList)) yield glb(p1, p2)
        ScFunctionType(lub(rt1, rt2), px)
      }
      //Tuple types
      case (ScTupleType(comps1), ScTupleType(comps2))
        if comps1.length == comps2.length => {
        val cx = for ((c1, c2) <- comps1.toList.zip(comps2.toList)) yield lub(c1, c2)
        ScTupleType(cx)
      }
      case (ScTypeVariable(_, Nil, _, upper), _) => lub(upper, t2)
      case (_, ScTypeVariable(_, Nil, _, upper)) => lub(t1, upper)
      case (ScTypeAliasType(_, Nil, _, upper), _) => lub(upper, t2)
      case (_, ScTypeAliasType(_, Nil, _, upper)) => lub(t1, upper)
      case (s: ScSingletonType, _) => lub(s.pathType, t2)
      case (_, s: ScSingletonType) => lub(t1, s.pathType)
      case _ => Any //todo
    }
  }
}