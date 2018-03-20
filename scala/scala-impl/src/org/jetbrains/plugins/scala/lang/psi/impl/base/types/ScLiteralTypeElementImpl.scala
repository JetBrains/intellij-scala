package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType

class ScLiteralTypeElementImpl(val node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteralTypeElement {
  override protected def innerType: TypeResult =
    ScLiteralImpl.getLiteralType(getLiteralNode, this) match {
      case Right(l) if getLiteral.allowLiteralTypes => Right(ScLiteralType.apply(getLiteral.getValue, l))
      case _ => Failure(ScalaBundle.message("wrong.type.no.literal.types", getText))
    }

  override def getLiteral: ScLiteral = getFirstChild.asInstanceOf[ScLiteral]

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case scalaVisitor: ScalaElementVisitor => scalaVisitor.visitTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}
