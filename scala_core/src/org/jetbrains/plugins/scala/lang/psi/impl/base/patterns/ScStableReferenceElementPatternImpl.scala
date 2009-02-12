package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import api.base.patterns.ScStableReferenceElementPattern
import com.intellij.lang.ASTNode

/**
 * @author ilyas
 */

class ScStableReferenceElementPatternImpl(node : ASTNode) extends ScPatternImpl(node) with ScStableReferenceElementPattern {

  override def toString: String = "StableElementPattern"

}