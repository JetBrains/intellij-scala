package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

trait ScPatternList extends ScalaPsiElement {

  def bindings: Seq[ScBindingPattern]

  def patterns: Seq[ScPattern]

  /**
   * This method means that Pattern list has just reference patterns:
   * val x, y, z = 44
   */
  def simplePatterns: Boolean
}

object ScPatternList {
  def unapply(e: ScPatternList): Some[Seq[ScPattern]] = Some(e.patterns)
}