package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import psi.types.result.TypingContext
import psi.types.ScType;
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScAnnotTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScAnnotTypeElement {
  override def toString: String = "TypeWithAnnotation"

  protected def innerType(ctx: TypingContext) = typeElement.getType(ctx)
}