package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import psi.types.ScType
import psi.types.result.{Success, TypeResult, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScCompositePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScCompositePattern{

  override def toString: String = "CompositePattern"

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    expectedType match {
      case Some(expected) => Success(expected, Some(this))
      case _ => super.getType(ctx) //Failure
    }
  }
}