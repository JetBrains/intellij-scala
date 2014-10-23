package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
 * Pavel Fatin
 */

object Qualifier {
  def unapply(e: ScReferenceElement): Option[ScalaPsiElement] = e.qualifier
}