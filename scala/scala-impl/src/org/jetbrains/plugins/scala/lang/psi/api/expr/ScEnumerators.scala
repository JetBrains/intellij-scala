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

  def forBindings: collection.Seq[ScForBinding]

  def generators: collection.Seq[ScGenerator]

  def guards: collection.Seq[ScGuard]

  def namings: collection.Seq[ScPatterned]

  def patterns: collection.Seq[ScPattern]
}
