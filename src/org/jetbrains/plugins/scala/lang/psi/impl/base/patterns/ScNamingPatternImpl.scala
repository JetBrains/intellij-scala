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
import psi.types.result.{TypeResult, Failure, Success, TypingContext}
import psi.types.{Bounds, ScType}

/**
 * @author Alexander Podkhalyuzin
 */

class ScNamingPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamingPattern {
  override def toString: String = "NamingPattern"

  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType(ScalaTokenTypes.tUNDER) != null

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    if (getLastChild.isInstanceOf[ScSeqWildcard]) {
      return expectedType match {
        case Some(x) => Success(x, Some(this))
        case _ =>  Failure("No expected type for wildcard naming", Some(this))
      }
    }
    if (named == null) Failure("Cannot infer type", Some(this))
    else named.getType(ctx)
  }
}