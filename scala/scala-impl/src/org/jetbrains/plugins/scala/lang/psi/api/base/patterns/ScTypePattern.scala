package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScTypePattern extends ScalaPsiElement {
  def typeElement: ScTypeElement = findChild[ScTypeElement].get
}