package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScPatternListBase extends ScalaPsiElementBase { this: ScPatternList =>

  def bindings: Seq[ScBindingPattern]

  def patterns: Seq[ScPattern]

  /**
   * This method means that Pattern list has just reference patterns:
   * val x, y, z = 44
   */
  def simplePatterns: Boolean
}

abstract class ScPatternListCompanion {
  def unapply(e: ScPatternList): Some[Seq[ScPattern]] = Some(e.patterns)
}