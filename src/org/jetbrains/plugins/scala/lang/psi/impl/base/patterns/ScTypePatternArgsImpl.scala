package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScTypePatternArgsImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypePatternArgs{
  override def toString: String = "TypePatternArguments"
}