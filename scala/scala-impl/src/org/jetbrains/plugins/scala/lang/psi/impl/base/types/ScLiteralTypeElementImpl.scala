package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.annotator.ScLiteralTypeElementAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScLiteralTypeElementImpl(val node: ASTNode) extends ScalaPsiElementImpl(node)
  with ScLiteralTypeElement with ScLiteralTypeElementAnnotator {

  override protected def innerType: TypeResult =
    ScLiteralType.kind(getLiteralNode, this) match {
      case Some(kind) => Right(ScLiteralType(getLiteral.getValue, kind).blockWiden())
      case _ => Failure(ScalaBundle.message("wrong.psi.for.literal.type", getText))
    }

  override def getLiteral: ScLiteral = getFirstChild.asInstanceOf[ScLiteral]
}
