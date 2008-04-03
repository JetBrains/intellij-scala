package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode


/** 
 * @author ilyas
 */

class ScSeqWildcardImpl (node: ASTNode) extends ScalaPsiElementImpl(node) with ScSeqWildcard {

  override def toString: String = "Sequence Wildcard"

}