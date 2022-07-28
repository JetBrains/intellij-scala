package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

object Qualifier {
  def unapply(e: ScReference): Option[ScalaPsiElement] = e.qualifier
}