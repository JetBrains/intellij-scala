package org.jetbrains.plugins.scala
package lang
package psi
package types

/** 
* @author ilyas
*/

case class ScMethodType(returnType: ScType, params: List[ScType]) extends ScType {
  override def equiv (t : ScType) : Boolean = {
    t match {
      case ScMethodType(returnType1, params1) => {
        if (!(returnType equiv returnType1)) false
        else {
          for ((p1, p2) <- params zip params1)  if (!(p1 equiv p2)) return false
          true
        }
      }
      case _ => false
    }
  }
}