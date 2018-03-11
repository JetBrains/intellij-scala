package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType

class ScLiteralTypeElementImpl(val node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteralTypeElement {
  override protected def innerType: TypeResult = Right(ScLiteralType(this))

  override def getLiteralText: String = node.getText

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case scalaVisitor: ScalaElementVisitor => scalaVisitor.visitTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}
