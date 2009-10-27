package org.jetbrains.plugins.scala.lang.psi.types.nonvalue

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam

/**
 * @author ilyas
 */

trait NonValueType extends ScType {
  def isValue = false
}

/**
 * see SLS 3.3.2
 */
case class ScPolymorphicMethodType(returnType: ScType, params: Seq[ScType], typeParams: Seq[ScTypeParam]) extends NonValueType {

  // todo rewrite me!
  override def equiv(t: ScType): Boolean = {
    t match {
      case ScPolymorphicMethodType(returnType1, params1, typeParams1) => {
        if (!(returnType equiv returnType1)) false
        else {
          for ((p1, p2) <- params zip params1) {
            if (!(p1 equiv p2)) return false
          }
          true
        }
      }
      case _ => false
    }
  }
}

/**
 * see SLS 3.3.1
 */
case class ScMethodType(rt: ScType, prms: Seq[ScType])
        extends ScPolymorphicMethodType(rt, prms, Nil)


/**
 * see SLS 3.3.3
 */
case class ScTypeConstructorType(internal: ScType, params: Seq[ScTypeParam]) extends NonValueType {


}
