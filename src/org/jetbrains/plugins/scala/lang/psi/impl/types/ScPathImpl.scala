package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

import org.jetbrains.plugins.scala.lang.psi._

class ScPathImpl2( node : ASTNode ) extends ScalaExpression(node) {
      override def toString: String = "Path"
}