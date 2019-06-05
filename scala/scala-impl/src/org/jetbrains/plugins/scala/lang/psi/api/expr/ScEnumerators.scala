package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScEnumerators extends ScalaPsiElement {

  def forBindings: Seq[ScForBinding]

  def generators: Seq[ScGenerator]

  def guards: Seq[ScGuard]

  def namings: Seq[ScPatterned]

  def patterns: Seq[ScPattern]
}
