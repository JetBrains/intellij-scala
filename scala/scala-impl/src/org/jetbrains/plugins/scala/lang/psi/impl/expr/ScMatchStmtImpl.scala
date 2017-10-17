package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScMatchStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMatchStmt {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "MatchStatement"

  protected override def innerType: TypeResult[ScType] = {
    val branchTypes = getBranches.map(_.getType())
    collectFailures(branchTypes, Nothing)(_.foldLeft(Nothing: ScType)(_.lub(_, checkWeak = true)))
  }
}