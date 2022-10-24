package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScTypes extends ScalaPsiElement {
  def types: Seq[ScTypeElement] = findChildren[ScTypeElement]
}