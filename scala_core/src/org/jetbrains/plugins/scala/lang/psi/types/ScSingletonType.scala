package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author ilyas
*/

case class ScSingletonType(path: ScReferenceElement) extends ScType {
  override def equiv(t : ScType)= t match {
    case ScSingletonType(path1) => path eq path1
    case _ => false
  }

  

}