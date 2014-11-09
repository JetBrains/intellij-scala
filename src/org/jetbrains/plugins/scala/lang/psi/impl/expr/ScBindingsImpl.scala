package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScBindingsImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScBindings{
  override def toString: String = "ParameterList"
}