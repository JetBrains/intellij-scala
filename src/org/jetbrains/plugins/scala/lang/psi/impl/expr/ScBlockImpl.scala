package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._


/**
* @author ilyas
*/
class ScBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScBlock {
  override def toString: String = "BlockOfExpressions"
}