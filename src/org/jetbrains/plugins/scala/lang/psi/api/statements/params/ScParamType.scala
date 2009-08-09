package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScParameterType extends ScalaPsiElement {

  def typeElement: ScTypeElement

  def isRepeatedParameter: Boolean = getLastChild.getNode.getElementType match {
    case ScalaTokenTypes.tIDENTIFIER if (getLastChild.getText == "*") => true
    case _ => false
  }
}