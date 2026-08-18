package org.jetbrains.plugins.scala.lang.psi.impl.base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, types}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result

final class ScLiteralTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node)
  with types.ScLiteralTypeElement {

  // A written down literal type is never widened, simply because nothing infers it, see
  // org.jetbrains.plugins.scala.lang.psi.types.Widening
  override protected def innerType: result.TypeResult = getLiteral.getNonValueType()

  override def getLiteral: ScLiteral = getFirstChild.asInstanceOf[ScLiteral]

  override def isSingleton: Boolean = getLiteral.isSimpleLiteral
}
