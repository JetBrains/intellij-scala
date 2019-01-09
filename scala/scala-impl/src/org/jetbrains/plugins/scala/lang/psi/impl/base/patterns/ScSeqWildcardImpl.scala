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

class ScSeqWildcardImpl (node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScSeqWildcard {
  // The SeqWildCard pattern is never irrefutable, with the exception that it occurs
  // in the exact position of an Constructor pattern.
  // See
  //   ScConstructorPattern.extractsRepeatedParameterIrrefutably
  override def isIrrefutableFor(t: Option[ScType]): Boolean = false

  override def toString: String = "Sequence Wildcard"

}