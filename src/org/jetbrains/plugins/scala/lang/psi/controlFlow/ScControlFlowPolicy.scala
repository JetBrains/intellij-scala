package org.jetbrains.plugins.scala
package lang.psi.controlFlow

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
 * Nikolay.Tropin
 * 2014-04-14
 */
trait ScControlFlowPolicy {
  def isElementAccepted(named: PsiNamedElement): Boolean

  def usedVariable(ref: ScReferenceElement): Option[PsiNamedElement] = ref.resolve() match {
    case named: PsiNamedElement if isElementAccepted(named) => Some(named)
    case _ => None
  }
}
