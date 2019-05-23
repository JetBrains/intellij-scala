package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, result}

class ScLiteralTypeElementImpl(val node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteralTypeElement {

  override protected def innerType: result.TypeResult =
    ScLiteralType.inferType(getLiteral, allowWiden = false)

  override def getLiteral: ScLiteral = getFirstChild.asInstanceOf[ScLiteral]
}
