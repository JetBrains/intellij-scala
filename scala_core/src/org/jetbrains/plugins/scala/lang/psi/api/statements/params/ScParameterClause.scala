package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 21.03.2008
*/

trait ScParameterClause extends ScalaPsiElement {

  def getParameters: Seq[ScParameter]

  def getParametersAsString: String

}