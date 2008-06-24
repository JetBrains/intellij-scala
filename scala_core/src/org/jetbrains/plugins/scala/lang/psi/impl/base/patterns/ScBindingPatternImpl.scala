package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import lexer.ScalaTokenTypes
import api.base.patterns.ScBindingPattern
import psi.types._
import com.intellij.lang.ASTNode

abstract class ScBindingPatternImpl(node: ASTNode) extends ScPatternImpl(node) with ScBindingPattern {

  def nameId = if (!isWildcard) {
    findChildByType(ScalaTokenTypes.tIDENTIFIER)
  } else findChildByType(ScalaTokenTypes.tUNDER)

  override def getName = if (!isWildcard) nameId.getText
                         else "_" //todo make model consistent
  //else throw new UnsupportedOperationException("Wildcard pattern has no name!")

  def isWildcard = findChildByType(ScalaTokenTypes.tUNDER) != null
}