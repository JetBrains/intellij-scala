package org.jetbrains.plugins.scala.lang.psi.api.base.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScTypeArgument extends ScalaPsiElement {
  def typeElement: Option[ScTypeElement]

  def name: Option[String]

  def nameElement: Option[PsiElement]

  final def isNamed: Boolean =
    name.isDefined
}
