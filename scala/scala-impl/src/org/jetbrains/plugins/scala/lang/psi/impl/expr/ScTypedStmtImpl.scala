package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScTypedStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypedStmt {
  override def toString: String = "TypedStatement"

  protected override def innerType: TypeResult[ScType] = {
    typeElement match {
      case Some(te) => te.getType()
      case None if !expr.isInstanceOf[ScUnderscoreSection] => expr.getType()
      case _ => Failure("Typed statement is not complete for underscore section", Some(this))
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTypedStmt(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitTypedStmt(this)
      case _ => super.accept(visitor)
    }
  }
}