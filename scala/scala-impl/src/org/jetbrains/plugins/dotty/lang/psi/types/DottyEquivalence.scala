package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.openapi.util.Computable
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
trait DottyEquivalence extends api.Equivalence {
  typeSystem: api.TypeSystem =>

  override protected def equivComputable(key: Key): Computable[ConstraintsResult] =
    () => ConstraintsResult.Left
}
