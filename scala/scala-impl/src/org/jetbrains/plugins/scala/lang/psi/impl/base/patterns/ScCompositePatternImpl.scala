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
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScCompositePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScCompositePattern {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "CompositePattern"

  override def `type`(): TypeResult[ScType] = {
    this.expectedType match {
      case Some(expected) => Right(expected)
      case _ => super.`type`() //Failure
    }
  }
}