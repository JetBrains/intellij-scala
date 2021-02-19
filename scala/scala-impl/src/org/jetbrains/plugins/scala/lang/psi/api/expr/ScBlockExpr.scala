package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScBlockExprBase extends ScExpressionBase with ScBlockBase with ScControlFlowOwnerBase { this: ScBlockExpr =>
  def asSimpleExpression: Option[ScExpression] = Some(exprs) collect {
    case Seq(it) if !it.is[ScBlockExpr] => it
  }

  def caseClauses: Option[ScCaseClauses] = findChild[ScCaseClauses]

  override def getControlFlow: Seq[Instruction] = {
    if (isAnonymousFunction) super.getControlFlow
    else {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScControlFlowOwner])
      parent.getControlFlow
    }
  }

  override def controlFlowScope: Option[ScCaseClauses] = if (isAnonymousFunction) caseClauses else None
}

abstract class ScBlockExprCompanion {
  object Expressions {
    def unapplySeq(e: ScBlockExpr): Some[Seq[ScExpression]] = Some(e.exprs)
  }

  object Statements {
    def unapplySeq(e: ScBlockExpr): Some[Seq[ScBlockStatement]] = Some(e.statements)
  }
}