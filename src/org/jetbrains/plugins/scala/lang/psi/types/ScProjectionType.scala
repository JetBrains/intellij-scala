package org.jetbrains.plugins.scala
package lang
package psi
package types


import api.base.ScReferenceElement
import com.intellij.psi.{PsiNamedElement, PsiMember}
import resolve._
import impl.toplevel.synthetic.ScSyntheticClass

/**
* @author ilyas
*/

case class ScProjectionType(projected: ScType, ref: ScReferenceElement) extends ValueType {
  def resolveResult = ref.bind

  lazy val element: Option[PsiNamedElement] = resolveResult.map(_.element)
  
  override def equiv(t : ScType) = t match {
    case ScProjectionType(p1, ref1) => ref1.refName == ref.refName && (projected equiv p1)
    case ScDesignatorType(des) => projected match {
      case ScSingletonType(path) => {
        val processor = new ResolveProcessor(StdKinds.stableClass, ref.refName)
        processor.processType(projected, path)
        if (processor.candidates.size == 1) {
          val a1 = processor.candidates.apply(0).element
          val res = a1 eq des
          res
        }
        else false
      }
      case _ => false
    }
    case t: StdType => {
      element match {
        case Some(synth: ScSyntheticClass) => synth.t equiv t
        case _ => false
      }
    }
    case _ => false
  }
}