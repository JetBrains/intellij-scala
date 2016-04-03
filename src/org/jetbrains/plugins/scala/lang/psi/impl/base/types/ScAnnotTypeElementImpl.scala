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

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScAnnotTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScAnnotTypeElement {
  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitAnnotTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => accept(s)
      case _ => super.accept(visitor)
    }
  }
}