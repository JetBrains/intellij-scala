package org.jetbrains.plugins.scala
package lang.psi.controlFlow.impl

import org.jetbrains.plugins.scala.lang.psi.controlFlow.ScControlFlowPolicy
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScClassParameter}

/**
 * Nikolay.Tropin
 * 2014-04-14
 */
object LocalsControlFlowPolicy extends ScControlFlowPolicy {
  override def isElementAccepted(element: PsiElement): Boolean = {
    element match {
      case cp: ScClassParameter => false
      case param: ScParameter => true
      case member: ScMember => member.isLocal
      case _ => false
    }
  }
}
