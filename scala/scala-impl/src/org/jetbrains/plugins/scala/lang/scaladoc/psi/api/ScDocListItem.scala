package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDocListItem extends ScalaPsiElement {

  /** @return element containing item type text e.g. "-", "1.", "i." */
  def headToken: ScPsiDocToken
}