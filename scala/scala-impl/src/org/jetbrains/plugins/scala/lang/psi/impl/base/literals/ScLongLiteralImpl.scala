package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import java.lang.{Long => JLong}

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScLongLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

final class ScLongLiteralImpl(node: ASTNode,
                              override val toString: String)
  extends NumberLiteralImplBase(node, toString)
    with ScLongLiteral {

  override protected def wrappedValue(value: JLong) =
    ScLongLiteralImpl.Value(value)

  override protected def parseNumber(text: String): JLong =
    PsiLiteralUtil.parseLong(text)
}

object ScLongLiteralImpl {

  final case class Value(override val value: JLong)
    extends NumberLiteralImplBase.Value(value) {

    override def negate = Value(-value)

    override def presentation: String = super.presentation + 'L'

    override def wideType(implicit project: Project): ScType = api.Long
  }
}
