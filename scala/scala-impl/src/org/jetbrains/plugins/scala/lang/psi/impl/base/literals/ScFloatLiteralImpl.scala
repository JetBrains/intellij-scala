package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import java.lang.{Float => JFloat}

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiLiteralUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScFloatLiteral

final class ScFloatLiteralImpl(node: ASTNode,
                               override val toString: String)
  extends NumberLiteralImplBase(node, toString) with ScFloatLiteral {

  override protected type V = JFloat

  override protected def parseNumber(text: String): JFloat =
    PsiLiteralUtil.parseFloat(text)
}
