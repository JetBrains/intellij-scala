package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScMatchTypeCasesBase extends ScalaPsiElementBase { this: ScMatchTypeCases =>
  def firstCase: ScMatchTypeCase
  def cases: Seq[ScMatchTypeCase]
}

abstract class ScMatchTypeCasesCompanion {
  def unapplySeq(cases: ScMatchTypeCases): Some[Seq[ScMatchTypeCase]] = Some(cases.cases)
}