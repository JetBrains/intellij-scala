package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScEnumerators extends ScalaPsiElement {

  def enumerators: Seq[ScEnumerator]

  def generators: Seq[ScGenerator]

  def guards: Seq[ScGuard]

  def namings: Seq[{def pattern: ScPattern}]

}