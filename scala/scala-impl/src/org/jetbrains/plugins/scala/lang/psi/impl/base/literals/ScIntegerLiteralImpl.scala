package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral

final class ScIntegerLiteralImpl(node: ASTNode,
                                 override val toString: String)
  extends NumberLiteralImplBase(node, toString) with ScIntegerLiteral {

  override protected type V = Integer

  override protected def parseNumber(text: String): Integer =
    PsiLiteralUtil.parseInteger(text)
}
