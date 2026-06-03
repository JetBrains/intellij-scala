package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDocList extends ScalaPsiElement with ScDocDescriptionPart {
  def items: Seq[ScDocListItem]
}