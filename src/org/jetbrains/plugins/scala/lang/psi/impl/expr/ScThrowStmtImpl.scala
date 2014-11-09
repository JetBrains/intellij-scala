package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

/**
* @author Alexander Podkhalyuzin, ilyas
*/

class ScThrowStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScThrowStmt {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ThrowStatement"

  protected override def innerType(ctx: TypingContext) = Success(Nothing, Some(this))

  def body = findChild(classOf[ScExpression])
}