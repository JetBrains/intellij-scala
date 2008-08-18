package org.jetbrains.plugins.scala.lang.psi.types

object Bounds {
  def glb(t1 : ScType, t2 : ScType) = {
    if (t1.conforms(t2)) t1
    else if (t2.conforms(t1)) t2
    else new ScCompoundType(Array(t1, t2), Seq.empty, Seq.empty) 
  }

  def lub(t1 : ScType, t2 : ScType) : ScType = {
    if (t1.conforms(t2)) t2
    else if (t2.conforms(t1)) t1
    else (t1, t2) match {
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