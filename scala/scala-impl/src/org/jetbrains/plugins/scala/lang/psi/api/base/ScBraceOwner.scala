package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScBraceOwner extends ScalaPsiElement {
  def isEnclosedByBraces: Boolean
}
