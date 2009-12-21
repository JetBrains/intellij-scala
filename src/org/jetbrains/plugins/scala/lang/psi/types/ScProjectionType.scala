package org.jetbrains.plugins.scala
package lang
package psi
package types


import api.base.ScReferenceElement
import resolve._
import impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.psi.{PsiClass, PsiNamedElement}
import api.toplevel.typedef.{ScTrait, ScTypeDefinition, ScClass}

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
        val processor = new ResolveProcessor(StdKinds.stableClass, ref.refName)
        processor.processType(projected, path)
        if (processor.candidates.size == 1) {
          val namedElement = processor.candidates.apply(0).element
          //todo: fix for case, when sources are attached, fix this case and remove this quickfix.
          namedElement match {
            case clazz: ScClass => {
              if (!des.isInstanceOf[ScClass]) return false
              return clazz.getQualifiedName == des.asInstanceOf[ScClass].getQualifiedName
            }
            case clazz: ScTrait => {
              if (!des.isInstanceOf[ScTrait]) return false
              return clazz.getQualifiedName == des.asInstanceOf[ScTrait].getQualifiedName
            }
            case td: ScTypeDefinition => return false
            case p: PsiClass => {
              if (des.isInstanceOf[ScTypeDefinition] || !des.isInstanceOf[PsiClass]) return false
              return p.getQualifiedName == des.asInstanceOf[PsiClass].getQualifiedName
            }
            case _ =>
          }
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
    case t: StdType => {
      element match {
        case Some(synth: ScSyntheticClass) => synth.t equiv t
        case _ => false
      }
    }
    case _ => false
  }
}