package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScIdList extends ScalaPsiElement {
  def fieldIds: Seq[ScFieldId]
}