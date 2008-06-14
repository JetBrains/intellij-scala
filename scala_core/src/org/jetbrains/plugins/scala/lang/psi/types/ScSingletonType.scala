package org.jetbrains.plugins.scala.lang.psi.types

import api.expr.ScReferenceExpression

/** 
* @author ilyas
*/

case class ScSingletonType(path: ScReferenceExpression) extends ScType {
  override def equiv(t : ScType)= t match {
    case ScSingletonType(path1) => {
      def equiv(e1 : ScReferenceExpression, e2 : ScReferenceExpression) : Boolean = {
        e1.bind == e2.bind && ((e1.qualifier, e2.qualifier) match {
          case (Some (q1 : ScReferenceExpression), Some (q2 : ScReferenceExpression)) => equiv(q1, q2)
          case (None, None) => true
          case _ => false
        })
      }
      equiv(path, path1)
    }
    case _ => false
  }

  

}