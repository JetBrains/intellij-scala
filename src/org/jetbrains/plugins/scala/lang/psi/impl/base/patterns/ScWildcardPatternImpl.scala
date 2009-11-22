package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.lang.ASTNode
import com.intellij.psi._
import psi.types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScWildcardPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScWildcardPattern {
  override def isIrrefutableFor(t: Option[ScType]): Boolean = true

  override def toString: String = "WildcardPattern"
}