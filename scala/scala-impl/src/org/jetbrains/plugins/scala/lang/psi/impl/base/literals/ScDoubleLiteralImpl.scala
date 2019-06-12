package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import java.lang.{Double => JDouble}

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScDoubleLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

final class ScDoubleLiteralImpl(node: ASTNode,
                                override val toString: String)
  extends NumberLiteralImplBase(node, toString)
    with ScDoubleLiteral {

  override protected def wrappedValue(value: JDouble) =
    ScDoubleLiteralImpl.Value(value)

  override protected def parseNumber(text: String): JDouble =
    PsiLiteralUtil.parseDouble(text)
}

object ScDoubleLiteralImpl {

  final case class Value(override val value: JDouble)
    extends NumberLiteralImplBase.Value(value) {

    override def negate = Value(-value)

    override def wideType(implicit project: Project): ScType = api.Double
  }
}
