package org.jetbrains.plugins.scala.lang.psi.impl.base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, types}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, result}

final class ScLiteralTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node)
  with types.ScLiteralTypeElement {

  override protected def innerType: result.TypeResult =
    getLiteral.getNonValueType().map {
      case literalType: ScLiteralType => literalType.blockWiden
      case resultType => resultType
    }

  override def getLiteral: ScLiteral = getFirstChild.asInstanceOf[ScLiteral]

  override def isSingleton: Boolean = getLiteral.isSimpleLiteral
}
