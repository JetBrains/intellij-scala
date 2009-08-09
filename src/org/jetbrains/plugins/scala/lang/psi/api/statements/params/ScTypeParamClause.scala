package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeParamClause extends ScalaPsiElement {
  def typeParameters() : Seq[ScTypeParam]
}