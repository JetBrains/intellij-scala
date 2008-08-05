package org.jetbrains.plugins.scala.lang.psi.types

object Bounds {
  def glb(t1 : ScType, t2 : ScType) = {
    if (t1.conforms(t2)) t1
    else if (t2.conforms(t1)) t2
    else new ScCompoundType(Array(t1, t2), Seq.empty, Seq.empty) 
  }

  def lub(t1 : ScType, t2 : ScType) = {
    if (t1.conforms(t2)) t2
    else if (t2.conforms(t1)) t1
    else Any //todo 
  }
}