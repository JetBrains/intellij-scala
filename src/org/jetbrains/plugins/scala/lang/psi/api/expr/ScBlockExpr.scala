package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{Instruction, ScControlFlowPolicy}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScBlockExpr extends ScExpression with ScBlock with ScControlFlowOwner {
  def caseClauses: Option[ScCaseClauses] = findChild(classOf[ScCaseClauses])

  override def getControlFlow(policy: ScControlFlowPolicy): Seq[Instruction] = {
    if (isAnonymousFunction) super.getControlFlow(policy)
    else {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScControlFlowOwner])
      parent.getControlFlow(policy)
    }
  }

  override def controlFlowScope = if (isAnonymousFunction) caseClauses else None
}