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
      case (ScFunctionType(rt1, p1), ScFunctionType(rt2, p2)) if p1.length == p2.length =>
        ScFunctionType(lub(rt1, rt2), p1.toList.zip(p2.toList).map{case (t1, t2) => glb(t1, t2)})
      case (ScTupleType(c1), ScTupleType(c2)) if c1.length == c2.length =>
        ScTupleType(c1.toList.zip(c2.toList).map{case (t1, t2) => lub(t1, t2)})

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