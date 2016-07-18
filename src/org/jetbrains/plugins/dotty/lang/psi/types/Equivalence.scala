package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
object Equivalence extends api.Equivalence {
  override implicit lazy val typeSystem = DottyTypeSystem

  override protected def computable(left: ScType, right: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean) = {
    () => (left match {
      case DottyNoType => false
      case _ if left eq right => true
      case _ => (left conforms right) && (right conforms left)
    }, substitutor)
  }
}
