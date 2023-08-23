package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr

import scala.annotation.tailrec

object WithParenthesesStripped {

  /**
   * Returns: `element` for `element`, `(element)`, `((element))`, etc.
   */
  def unapply(stmt: ScalaPsiElement): Option[ScalaPsiElement] = Some(getInnermostNonParen(stmt))

  @tailrec
  private def getInnermostNonParen(stmt: ScalaPsiElement): ScalaPsiElement = stmt match {
    case ScParenthesisedExpr(e) => getInnermostNonParen(e)
    case _ => stmt
  }
}
