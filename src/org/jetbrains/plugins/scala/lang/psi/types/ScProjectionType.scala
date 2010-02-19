package org.jetbrains.plugins.scala
package lang
package psi
package types


import resolve._
import impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.psi.{PsiClass, PsiNamedElement}
import api.toplevel.typedef.{ScTrait, ScTypeDefinition, ScClass}
import api.base.{ScPathElement, ScStableCodeReferenceElement, ScReferenceElement}

/**
* @author ilyas
*/

case class
ScProjectionType(projected: ScType, ref: ScReferenceElement) extends ValueType {
  def resolveResult = ref.bind

  lazy val element: Option[PsiNamedElement] = resolveResult.map(_.element)
  
  override def equiv(t : ScType): Boolean = t match {
    case ScProjectionType(p1, ref1) => ref1.refName == ref.refName && (projected equiv p1)
    case ScDesignatorType(des) => projected match {
      case ScSingletonType(path) => {
        val processor = new ResolveProcessor(StdKinds.stableClass, ref, ref.refName)
        processor.processType(projected, path)
        if (processor.candidates.size == 1) {
          val namedElement = processor.candidates.apply(0).element
          val res = namedElement eq des
          res
        }
        else false
      }
      case ScDesignatorType(_) => resolveResult match {
        case Some(ScalaResolveResult(el: PsiNamedElement, _)) => el == des
        case _ => false
      }
      case _ => false
    }
    case ScSingletonType(path: ScPathElement) => path match {
      case ref: ScStableCodeReferenceElement => {
        ref.bind match {
          case Some(ScalaResolveResult(el, _)) => {
            this.resolveResult match {
              case Some(ScalaResolveResult(el2, _)) => el2 == el
              case _ => false
            }
          }
          case _ => false
        }
      }
      case _ => false
    }
    case AnyRef => AnyRef.equiv(this)
    case t: StdType => {
      element match {
        case Some(synth: ScSyntheticClass) => synth.t equiv t
        case _ => false
      }
    }
    case _ => false
  }
}