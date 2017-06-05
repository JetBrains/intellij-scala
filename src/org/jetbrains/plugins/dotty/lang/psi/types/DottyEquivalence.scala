package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.openapi.util.Computable
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
trait DottyEquivalence extends api.Equivalence {
  typeSystem: api.TypeSystem =>

  override protected def equivComputable(left: ScType, right: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean) = {
    new Computable[(Boolean, ScUndefinedSubstitutor)] {
      override def compute(): (Boolean, ScUndefinedSubstitutor) = (left match {
        case DottyNoType() => false
        case _ if left eq right => true
        case _ => (left conforms right) && (right conforms left)
      }, substitutor)
    }
  }
}
