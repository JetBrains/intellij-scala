package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

final class ScIntegerLiteralImpl(node: ASTNode,
                                 override val toString: String)
  extends NumberLiteralImplBase(node, toString)
    with ScIntegerLiteral {

  override protected def wrappedValue(value: Integer) =
    ScIntegerLiteralImpl.Value(value)

  override protected def parseNumber(text: String): Integer =
    PsiLiteralUtil.parseInteger(text)
}

object ScIntegerLiteralImpl {

  final case class Value(override val value: Integer)
    extends NumberLiteralImplBase.Value(value) {

    override def negate = Value(-value)

    override def wideType(implicit project: Project): ScType = api.Int
  }
}