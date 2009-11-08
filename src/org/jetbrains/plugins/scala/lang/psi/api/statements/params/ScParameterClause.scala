package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import types._
import types.result.TypingContext

/** 
* @author Alexander Podkhalyuzin
* Date: 21.03.2008
*/

trait ScParameterClause extends ScalaPsiElement {

  def parameters: Seq[ScParameter]
  def paramTypes: Seq[ScType] = parameters.map(_.getType(TypingContext.empty).getOrElse(Any))
  def isImplicit: Boolean
  def hasRepeatedParam: Boolean = parameters.length > 0 && parameters.apply(parameters.length - 1).isRepeatedParameter
}