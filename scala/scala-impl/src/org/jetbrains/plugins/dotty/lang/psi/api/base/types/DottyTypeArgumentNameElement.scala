package org.jetbrains.plugins.dotty.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
  * @author adkozlov
  */
trait DottyTypeArgumentNameElement extends ScalaPsiElement {
  def name: String
}
