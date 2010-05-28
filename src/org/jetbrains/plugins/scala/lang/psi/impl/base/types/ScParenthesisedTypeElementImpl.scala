package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import psi.types.result.{Success, TypingContext}

/**
* @author Alexander Podkhalyuzin, ilyas
*/

class ScParenthesisedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedTypeElement{
  override def toString: String = "TypeInParenthesis"

  protected def innerType(ctx: TypingContext) = {
    typeElement match {
      case Some(el) => el.getType(ctx)
      case None => Success(psi.types.Unit, Some(this))
    }
  }
}