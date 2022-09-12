package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

trait ScEnumerators extends ScalaPsiElement {

  def forBindings: Seq[ScForBinding]

  def generators: Seq[ScGenerator]

  def guards: Seq[ScGuard]

  def namings: Seq[ScPatterned]

  def patterns: Seq[ScPattern]
}
