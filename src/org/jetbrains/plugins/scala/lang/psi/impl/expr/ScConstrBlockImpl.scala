package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import api.expr._

/**
* @author Alexander.Podkhalyuzin 
*/

class ScConstrBlockImpl(node: ASTNode) extends ScBlockImpl(node) with ScConstrBlock {
  override def toString: String = "ConstructorBlock"




}