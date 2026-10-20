package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

trait ScTypeArgument extends ScalaPsiElement with Typeable {
  def typeElement: Option[ScTypeElement]

  def name: Option[String]

  def nameElement: Option[ScStableCodeReference]

  final def isNamed: Boolean =
    name.isDefined
}

object ScTypeArgument {
  object Named {
    def unapply(targ: ScTypeArgument): Option[(String, Option[ScTypeElement])] =
      targ.name.map(_ -> targ.typeElement)
  }
}
