package org.jetbrains.plugins.scala.lang.psi.types

import resolve._

/**
* @author ilyas
*/

case class ScProjectionType(projected: ScType, name: String) extends ScType {
  override def equiv(t : ScType) = t match {
    case ScProjectionType(p1, n1) => n1 == name && (projected equiv p1)
    case ScDesignatorType(des) => projected match {
      case ScSingletonType(path) => {
        val proc = new ResolveProcessor(StdKinds.stableClass, name)
        path.processType(projected, proc)
        if (proc.candidates.size == 1) proc.candidates.toArray(0).element eq des else false
      }
      case _ => false
    }
    case _ => false
  }
}