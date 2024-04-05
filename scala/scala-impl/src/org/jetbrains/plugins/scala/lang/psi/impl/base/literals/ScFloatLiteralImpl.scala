package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScFloatLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

import java.lang
import java.lang.{Float => JFloat}

final class ScFloatLiteralImpl(node: ASTNode,
                               override val toString: String)
  extends NumericLiteralImplBase(node, toString)
    with ScFloatLiteral {

  override protected def wrappedValue(value: JFloat): ScLiteral.Value[lang.Float] =
    ScFloatLiteralImpl.Value(value)

  override protected def fallbackType: ScType = api.Float

  override protected def parseNumber(text: String): JFloat =
    PsiLiteralUtil.parseFloat(text)

  override private[psi] def unwrappedValue(value: JFloat) =
    value.floatValue
}

object ScFloatLiteralImpl {

  final case class Value(override val value: JFloat)
    extends NumericLiteralImplBase.Value(value) {

    override def negate: NumericLiteralImplBase.Value[JFloat] = Value(-value)

    override def presentation: String = super.presentation + 'f'

    override def wideType(implicit project: Project): ScType = api.Float
  }
}