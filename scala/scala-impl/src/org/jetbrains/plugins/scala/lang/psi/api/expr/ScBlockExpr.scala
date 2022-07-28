package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction

trait ScBlockExpr extends ScExpression with ScBlock with ScControlFlowOwner {
  def asSimpleExpression: Option[ScExpression] = Some(exprs) collect {
    case Seq(it) if !it.is[ScBlockExpr] => it
  }

  def caseClauses: Option[ScCaseClauses] = findChild[ScCaseClauses]

  override def getControlFlow: Seq[Instruction] = {
    if (isPartialFunction) super.getControlFlow
    else {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScControlFlowOwner])
      parent.getControlFlow
    }
  }

  override def controlFlowScope: Option[ScCaseClauses] = if (isPartialFunction) caseClauses else None
}

object ScBlockExpr {
  object Expressions {
    def unapplySeq(e: ScBlockExpr): Some[Seq[ScExpression]] = Some(e.exprs)
  }

  object Statements {
    def unapplySeq(e: ScBlockExpr): Some[Seq[ScBlockStatement]] = Some(e.statements)
  }

  object withCaseClauses {
    def unapply(block: ScBlockExpr): Option[ScCaseClauses] = block.caseClauses
  }
}