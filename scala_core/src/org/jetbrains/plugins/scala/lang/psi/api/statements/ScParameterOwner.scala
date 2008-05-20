package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author ilyas
*/

trait ScParameterOwner extends ScalaPsiElement {
  def getParameters: Seq[ScParameter]
}