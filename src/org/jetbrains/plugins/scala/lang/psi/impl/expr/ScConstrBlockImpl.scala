package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import api.expr._

/**
* @author Alexander.Podkhalyuzin 
*/

class ScConstrBlockImpl(node: ASTNode) extends ScBlockImpl(node) with ScConstrBlock {
  override def toString: String = "ConstructorBlock"




}