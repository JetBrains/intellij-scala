package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgument, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScTypeArgumentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeArgument {
  override def toString: String = "TypeArgument"

  override def typeElement: Option[ScTypeElement] =
    findChild[ScTypeElement]

  override def nameElement: Option[ScStableCodeReference] =
    findChild[ScStableCodeReference]

  override def name: Option[String] =
    nameElement.map(_.refName)

  override def deleteChildInternal(child: ASTNode): Unit = {
    if (typeElement.exists(_.getNode == child)) this.delete()
    else super.deleteChildInternal(child)
  }

  override def `type`(): TypeResult = typeElement match {
    case Some(te) => te.`type`()
    case None     => Failure(ScalaBundle.message("no.type.element.type.arg", this.getText))
  }
}
