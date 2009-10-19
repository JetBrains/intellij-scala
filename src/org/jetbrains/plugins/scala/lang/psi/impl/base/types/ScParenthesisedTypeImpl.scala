package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import psi.types.result.TypingContext

/** 
* @author Alexander Podkhalyuzin, ilyas
*/

class ScParenthesisedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedTypeElement{
  override def toString: String = "TypeInParenthesis"

  override def getType(ctx: TypingContext) = wrap(typeElement).flatMap(_.getType(ctx))
}