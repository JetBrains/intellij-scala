package org.jetbrains.plugins.scala
package lang.psi.controlFlow.impl

import org.jetbrains.plugins.scala.lang.psi.controlFlow.ScControlFlowPolicy
import com.intellij.psi.PsiElement

/**
 * Nikolay.Tropin
 * 2014-04-14
 */
object AllVariablesControlFlowPolicy extends ScControlFlowPolicy {
  override def isElementAccepted(element: PsiElement): Boolean = true
}
