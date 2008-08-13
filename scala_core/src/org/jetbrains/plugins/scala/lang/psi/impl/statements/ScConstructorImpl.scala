package org.jetbrains.plugins.scala.lang.psi.impl.statements

import api.statements.ScConstructor
import com.intellij.lang.ASTNode

/**
 * @author ilyas
 */

class ScConstructorImpl (node: ASTNode) extends ScMemberImpl(node) with ScConstructor{

  override def toString: String = "ScConstructor"

}