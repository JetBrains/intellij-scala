package org.jetbrains.plugins.scala.lang.psi.impl.expressions
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

case class ScExprsImpl( node : ASTNode ) extends ScExprImpl(node) {
    override def toString: String = "Expression list"
}