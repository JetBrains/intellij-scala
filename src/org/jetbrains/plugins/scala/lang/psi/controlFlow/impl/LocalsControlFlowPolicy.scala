package org.jetbrains.plugins.scala
package lang.psi.controlFlow.impl

import org.jetbrains.plugins.scala.lang.psi.controlFlow.ScControlFlowPolicy
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScClassParameter}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticNamedElement

/**
 * Nikolay.Tropin
 * 2014-04-14
 */
object LocalsControlFlowPolicy extends ScControlFlowPolicy {
  override def isElementAccepted(named: PsiNamedElement): Boolean = {
    if (named.isInstanceOf[SyntheticNamedElement]) return false

    ScalaPsiUtil.nameContext(named) match {
      case cp: ScClassParameter => false
      case member: ScMember => member.isLocal
      case _ => true
    }
  }
}
