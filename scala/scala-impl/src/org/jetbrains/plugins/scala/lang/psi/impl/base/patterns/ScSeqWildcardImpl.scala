package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType

class ScSeqWildcardImpl (node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScSeqWildcard {
  // The SeqWildCard pattern is never irrefutable, with the exception that it occurs
  // in the exact position of an Constructor pattern.
  // See
  //   ScConstructorPattern.extractsRepeatedParameterIrrefutably
  override def isIrrefutableFor(t: Option[ScType]): Boolean = false

  override def toString: String = "Sequence Wildcard"
}