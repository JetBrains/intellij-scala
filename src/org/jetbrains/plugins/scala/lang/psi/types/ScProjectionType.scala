package org.jetbrains.plugins.scala
package lang
package psi
package types


import api.base.ScReferenceElement
import com.intellij.psi.{PsiNamedElement, PsiMember}
import resolve._

/**
* @author ilyas
*/

case class ScProjectionType(projected: ScType, ref: ScReferenceElement) extends ScType {
  def resolveResult = ref.bind

  lazy val element: Option[PsiNamedElement] = resolveResult.map(_.element)
  
  override def equiv(t : ScType) = t match {
    case ScProjectionType(p1, ref1) => ref1.refName == ref.refName && (projected equiv p1)
    case ScDesignatorType(des) => projected match {
      case ScSingletonType(path) => {
        val processor = new ResolveProcessor(StdKinds.stableClass, ref.refName)
        processor.processType(projected, path)
        if (processor.candidates.size == 1) processor.candidates.toArray(0).element eq des else false
      }
      case _ => false
    }
    case _ => false
  }
}