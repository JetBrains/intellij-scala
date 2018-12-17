package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.types.ScType


/** 
 * @author ilyas
 */

class ScSeqWildcardImpl (node: ASTNode) extends ScalaPsiElementImpl(node) with ScSeqWildcard {
  override def isIrrefutableFor(t: ScType): Boolean = true

  override def toString: String = "Sequence Wildcard"

}