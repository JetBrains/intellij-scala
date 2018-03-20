package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
* @author Alexander Podkhalyuzin, ilyas
*/

class ScParenthesisedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParenthesisedTypeElement {
  protected def innerType: TypeResult = innerElement match {
    case Some(el) => el.`type`()
    case None => Right(Unit)
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitParenthesisedTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitParenthesisedTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}