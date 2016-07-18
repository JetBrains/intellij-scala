package org.jetbrains.plugins.dotty.lang.psi.types

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author adkozlov
  */
object Conformance extends api.Conformance {
  override implicit lazy val typeSystem = DottyTypeSystem

  override protected def computable(left: ScType, right: ScType, visited: Set[PsiClass], checkWeak: Boolean) = () => (false, new ScUndefinedSubstitutor())

  private def isSubType(left: ScType, right: ScType) = right match {
    case DottyNoType => false
    case _ => if (left eq right) true else firstTry(left, right)
  }

  private def firstTry(left: ScType, right: ScType): Boolean = false
}
