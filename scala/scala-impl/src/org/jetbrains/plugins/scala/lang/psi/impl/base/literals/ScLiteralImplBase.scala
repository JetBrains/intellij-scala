package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScExpressionImplBase
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType, result}

abstract class ScLiteralImplBase(node: ASTNode,
                                 override val toString: String)
  extends ScExpressionImplBase(node)
    with ScLiteral {

  protected def wrappedValue(value: V): ScLiteral.Value[V]

  protected def fallbackType: ScType

  override protected def innerType: result.TypeResult = getValue match {
    case null =>
      Right(fallbackType)
    case value =>
      Right(
        ScLiteralType(wrappedValue(value))(getProject)
      )
  }

  override protected final def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitLiteral(this)
  }

  override def contentRange: TextRange = getTextRange

  override def contentText: String = {
    val rangeLocal = contentRange.shiftLeft(getTextRange.getStartOffset)
    rangeLocal.substring(getText)
  }
}
