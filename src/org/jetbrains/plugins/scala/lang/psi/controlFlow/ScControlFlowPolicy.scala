package org.jetbrains.plugins.scala
package lang.psi.controlFlow

import com.intellij.psi.PsiElement

/**
 * Nikolay.Tropin
 * 2014-04-14
 */
trait ScControlFlowPolicy {
  def isElementAccepted(element: PsiElement): Boolean
}
