package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult}

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScWildcardPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScWildcardPattern {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def isIrrefutableFor(t: Option[ScType]): Boolean = true

  override def toString: String = "WildcardPattern"

  override def getType(ctx: TypingContext.type): TypeResult[ScType] = this.expectedType match {
    case Some(x) => Success(x, Some(this))
    case _ => Failure("cannot determine expected type", Some(this))
  }
}