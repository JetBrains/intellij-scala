package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import psi.types.result.TypingContext
import psi.types.ScType;
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScAnnotTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScAnnotTypeElement {
  override def toString: String = "TypeWithAnnotation: " + getText

  protected def innerType(ctx: TypingContext) = typeElement.getType(ctx)

    override def accept(visitor: ScalaElementVisitor) {
        visitor.visitAnnotTypeElement(this)
      }

      override def accept(visitor: PsiElementVisitor) {
        visitor match {
          case s: ScalaElementVisitor => s.visitAnnotTypeElement(this)
          case _ => super.accept(visitor)
        }
      }
}