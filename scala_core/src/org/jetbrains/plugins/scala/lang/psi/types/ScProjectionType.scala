package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiMember
import resolve._

/**
* @author ilyas
*/

case class ScProjectionType(projected: ScType, name: String) extends ScType {
  lazy val resolveResult = projected match {
    case sin@ScSingletonType(path) => {
      val proc = new ResolveProcessor(StdKinds.stableClass, name)
      proc.processType(sin, path)
      if (proc.candidates.size == 1) {
        val r = proc.candidates.toArray(0)
        val res = r.element match {
          case mem : PsiMember if mem.getContainingClass != null =>
            new ScalaResolveResult(mem, r.substitutor.bindOuter(mem.getContainingClass, projected))
          case _ => r
        }
        Some(res)
      } else None
    }
    case _ => None
  }

  lazy val element = resolveResult match {
    case Some(r) => r.element
    case None => None
  }
  
  override def equiv(t : ScType) = t match {
    case ScProjectionType(p1, n1) => n1 == name && (projected equiv p1)
    case ScDesignatorType(des) => projected match {
      case ScSingletonType(path) => {
        val proc = new ResolveProcessor(StdKinds.stableClass, name)
        proc.processType(projected, path)
        if (proc.candidates.size == 1) proc.candidates.toArray(0).element eq des else false
      }
      case _ => false
    }
    case _ => false
  }
}