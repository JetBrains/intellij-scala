package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 21.03.2008
*/

trait ScParameterClause extends ScalaPsiElement {

  def parameters: Seq[ScParameter]
  def paramTypes: Seq[ScType] = parameters.map(_.calcType)
  def isImplicit: Boolean = getNode.findChildByType(ScalaTokenTypes.kIMPLICIT) != null
  def hasRepeatedParam: Boolean = parameters.length > 0 && parameters.apply(parameters.length - 1).isRepeatedParameter
}