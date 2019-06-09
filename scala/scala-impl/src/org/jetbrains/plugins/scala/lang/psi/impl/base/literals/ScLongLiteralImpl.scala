package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import java.lang.{Long => JLong}

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScLongLiteral

final class ScLongLiteralImpl(node: ASTNode,
                              override val toString: String)
  extends NumberLiteralImplBase(node, toString) with ScLongLiteral {

  override protected type V = JLong

  override protected def parseNumber(text: String): JLong =
    PsiLiteralUtil.parseLong(text)
}
