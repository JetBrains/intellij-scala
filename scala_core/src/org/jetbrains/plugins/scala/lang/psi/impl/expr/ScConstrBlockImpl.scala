package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.formatting.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScCodeBlockImpl

/**
* @author Alexander.Podkhalyuzin 
*/

class ScConstrBlockImpl(node: ASTNode) extends ScCodeBlockImpl(node) with ScConstrBlock {
  override def toString: String = "ConstructorBlock"




}