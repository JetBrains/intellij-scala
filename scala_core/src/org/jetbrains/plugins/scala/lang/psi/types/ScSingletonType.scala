package org.jetbrains.plugins.scala.lang.psi.types

import api.toplevel.ScTyped
import api.base.ScReferenceElement

/** 
* @author ilyas
*/

case class ScSingletonType(path: ScReferenceElement) extends ScType {
  val pathType = path.bind match {
    case None => Nothing
    case Some(r) => r.element match {
      case typed : ScTyped => typed.calcType
      case e => new ScDesignatorType(e)
    }
  }

  override def equiv(t : ScType) = t match {
    case ScSingletonType(path1) => {
      def equiv(e1 : ScReferenceElement, e2 : ScReferenceElement) : Boolean = {
        e1.bind == e2.bind && ((e1.qualifier, e2.qualifier) match {
          case (Some (q1 : ScReferenceElement), Some (q2 : ScReferenceElement)) => equiv(q1, q2)
          case (None, None) => true
          case _ => false
        })
      }
      equiv(path, path1)
    }
    case _ => false
  }
}