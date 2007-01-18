package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

class ScStableIdImpl( node : ASTNode ) extends ScSimpleExprImpl(node) {

  override def toString: String = "Stable Identifier"

  def getType() : PsiType = null

  def getPath : ScPathImpl = {
    if (getChildren.length == 1 ) null
    else {
      val first = getChildren()(0)
      first match {
        case path : ScPathImpl => path
        case _ => null
      }
    }
  }

}