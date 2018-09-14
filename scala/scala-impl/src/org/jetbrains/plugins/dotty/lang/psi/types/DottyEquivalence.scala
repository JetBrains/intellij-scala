package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.openapi.util.Computable
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
trait DottyEquivalence extends api.Equivalence {
  typeSystem: api.TypeSystem =>

  override protected def equivComputable(left: ScType, right: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean): Computable[ConstraintsResult] = {
    new Computable[ConstraintsResult] {
      override def compute(): ConstraintsResult = left match {
        case DottyNoType() => ConstraintsResult.Failure
        case _ if left eq right => substitutor
        case _ =>
          if ((left conforms right) && (right conforms left)) substitutor
          else ConstraintsResult.Failure
      }
    }
  }
}
