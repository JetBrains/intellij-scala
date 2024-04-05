package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScDoubleLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

import java.lang
import java.lang.{Double => JDouble}

final class ScDoubleLiteralImpl(node: ASTNode,
                                override val toString: String)
  extends NumericLiteralImplBase(node, toString)
    with ScDoubleLiteral {

  override protected def wrappedValue(value: JDouble): ScLiteral.Value[lang.Double] =
    ScDoubleLiteralImpl.Value(value)

  override protected def fallbackType: ScType = api.Double

  override protected def parseNumber(text: String): JDouble =
    PsiLiteralUtil.parseDouble(text)

  override private[psi] def unwrappedValue(value: JDouble) =
    value.doubleValue
}

object ScDoubleLiteralImpl {

  final case class Value(override val value: JDouble)
    extends NumericLiteralImplBase.Value(value) {

    override def negate: NumericLiteralImplBase.Value[JDouble] = Value(-value)

    override def wideType(implicit project: Project): ScType = api.Double
  }
}
