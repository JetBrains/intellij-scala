package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import com.intellij.lang.ASTNode

abstract class ScBindingPatternImpl(node: ASTNode) extends ScPatternImpl(node) with ScBindingPattern {

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getName = if (!isWildcard) nameId.getText else null

  def isWildcard = findChildByType(ScalaTokenTypes.tUNDER) != null


}