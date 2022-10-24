package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScTypePattern extends ScalaPsiElement {
  def typeElement: ScTypeElement = findChild[ScTypeElement].get
}