package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import _root_.org.jetbrains.plugins.scala.lang.psi.types.Unit
import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import collection.Set
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import psi.types.result.TypingContext

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScParenthesisedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedTypeElement{
  override def toString: String = "TypeInParenthesis"

  override def getType(ctx: TypingContext) = typeElement match {
    case Some(te) => te.getType(ctx)
    case None => Unit
  }
}