package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import api.base.patterns._
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScNamingPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamingPattern{
  override def toString: String = "NamingPattern"

  override def calcType = if (named == null) psi.types.Nothing else named.calcType //todo fix parser

  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType(ScalaTokenTypes.tUNDER) != null
}