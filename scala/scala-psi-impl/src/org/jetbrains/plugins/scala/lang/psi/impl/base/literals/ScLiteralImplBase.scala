package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.extensions.PsiElementExt
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

  /**
   * https://www.scala-lang.org/files/archive/spec/3.4/06-expressions.html#numeric-literal-conversion
   *
   * If the expected type is Byte, Short, Long or Char, and the expression e is an Int literal fitting in the range of that type,
   * it is converted to the same literal in that type.
   *
   * Likewise, if the expected type is Float or Double, and the expression e is a numeric literal (of any type) fitting in the range of that type,
   * it is converted to the same literal in that type.
  **/
  override protected def innerType(expectedType: Option[ScType]): result.TypeResult = {
    val stdTypes = projectContext.stdTypes
    import stdTypes.*

    val inScala3 = this.isInScala3File

    val convertedLiteralType = (expectedType, getValue) match {
      case (Some(tpe @ (Byte | Short | Long | Char)), _: Integer) if inScala3 => wrappedValue(getValue).to(tpe).map(ScLiteralType(_, this)(using getProject)).getOrElse(literalType)
      case (Some(tpe @ (Byte | Short | Char)), _: Integer) if !inScala3 => wrappedValue(getValue).to(tpe).map(ScLiteralType(_, this)(using getProject)).getOrElse(literalType)
      case (Some(tpe @ (Float | Double)), _: Number) if inScala3 => wrappedValue(getValue).to(tpe).map(ScLiteralType(_, this)(using getProject)).getOrElse(literalType)
      case _ => literalType
    }

    Right(convertedLiteralType)
  }

  override protected final def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitLiteral(this)
  }

  override def literalType: ScType = getValue match {
    case null =>
      fallbackType
    case value =>
      ScLiteralType(wrappedValue(value), psiElement = this)(using getProject)
  }

  override def contentRange: TextRange = getTextRange

  override def contentRangeInParent: TextRange = contentRange.shiftLeft(getTextRange.getStartOffset)

  override def contentText: String = {
    val range = contentRangeInParent
    range.substring(getText)
  }
}
