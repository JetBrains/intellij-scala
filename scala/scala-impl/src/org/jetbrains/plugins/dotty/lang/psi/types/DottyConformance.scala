package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
trait DottyConformance extends api.Conformance {
  typeSystem: api.TypeSystem =>

  override protected def conformsComputable(key: Key, visited: Set[PsiClass]): Computable[ConstraintsResult] =
    () => ConstraintsResult.Left
}
