package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import java.lang.{Double => JDouble}

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScDoubleLiteral

final class ScDoubleLiteralImpl(node: ASTNode,
                                override val toString: String)
  extends NumberLiteralImplBase(node, toString) with ScDoubleLiteral {

  override protected type V = JDouble

  override protected def parseNumber(text: String): JDouble =
    PsiLiteralUtil.parseDouble(text)
}
