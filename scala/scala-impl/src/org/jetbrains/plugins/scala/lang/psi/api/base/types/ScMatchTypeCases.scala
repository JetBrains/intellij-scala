package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScMatchTypeCases extends ScalaPsiElement {
  def firstCase: ScMatchTypeCase
  def cases: Seq[ScMatchTypeCase]
}

object ScMatchTypeCases {
  def unapplySeq(cases: ScMatchTypeCases): Some[Seq[ScMatchTypeCase]] = Some(cases.cases)
}