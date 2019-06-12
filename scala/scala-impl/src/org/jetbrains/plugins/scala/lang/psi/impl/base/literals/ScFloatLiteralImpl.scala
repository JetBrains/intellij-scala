package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import java.lang.{Float => JFloat}

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScFloatLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

final class ScFloatLiteralImpl(node: ASTNode,
                               override val toString: String)
  extends NumberLiteralImplBase(node, toString)
    with ScFloatLiteral {

  override protected def wrappedValue(value: JFloat) =
    ScFloatLiteralImpl.Value(value)

  override protected def parseNumber(text: String): JFloat =
    PsiLiteralUtil.parseFloat(text)
}

object ScFloatLiteralImpl {

  final case class Value(override val value: JFloat)
    extends NumberLiteralImplBase.Value(value) {

    override def negate = Value(-value)

    override def presentation: String = super.presentation + 'f'

    override def wideType(implicit project: Project): ScType = api.Float
  }
}