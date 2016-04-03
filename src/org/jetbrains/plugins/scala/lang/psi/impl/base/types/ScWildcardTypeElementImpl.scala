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
import org.jetbrains.plugins.scala.lang.psi.types.ScExistentialType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
* @author Alexander Podkhalyuzin
* Date: 11.04.2008
*/

class ScWildcardTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeBoundsOwnerImpl with ScWildcardTypeElement {
  protected def innerType(ctx: TypingContext) = {
    for {
      lower <- lowerBound
      upper <- upperBound
    } yield ScExistentialType.simpleExistential(lower, upper)
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitWildcardTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitWildcardTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}