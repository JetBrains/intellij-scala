package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScWildcardPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternImpl with ScWildcardPattern {

  override def isIrrefutableFor(t: Option[ScType]): Boolean = true

  override def toString: String = "WildcardPattern"

  override def `type`(): TypeResult = this.expectedType match {
    case Some(x) => Right(x)
    case _ => Failure("cannot determine expected type")
  }
}