package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, result}

abstract class ScLiteralImplBase(node: ASTNode,
                                 override val toString: String)
  extends expr.ScExpressionImplBase(node)
    with ScLiteral {

  protected def wrappedValue(value: V): ScLiteral.Value[V]

  override protected def innerType: result.TypeResult = getValue match {
    case null =>
      result.Failure(ScalaBundle.message("wrong.psi.for.literal.type", getText))
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

  // TODO all the methods are not applicable

  override def isString: Boolean = false

  override def isMultiLineString: Boolean = false
}
