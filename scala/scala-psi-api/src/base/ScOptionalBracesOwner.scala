package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{LBRACE_OR_COLON_TOKEN_SET, tCOLON, tLBRACE}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/** Scala 3 introduced optional braces, now template bodies, argument blocks, etc.
 * may be written as
 * {{{
 *   foo:
 *     bar
 *     baz
 * }}}
 * instead of
 * {{{
 *   foo {
 *     bar
 *     baz
 *   }
 * }}}
 *
 * Some blocks may be without braces and colon (e.g.: case clause blocks, function definitions, etc.)
 */
trait ScOptionalBracesOwner extends ScalaPsiElement {
  @inline
  def getEnclosingStartElement: Option[PsiElement] =
    this.firstChild.filter(child => LBRACE_OR_COLON_TOKEN_SET.contains(child.elementType))

  @inline def getLBrace: Option[PsiElement] =
    getEnclosingStartElement.filter(_.elementType == tLBRACE)

  @inline def getColon: Option[PsiElement] =
    getEnclosingStartElement.filter(_.elementType == tCOLON)

  def isEnclosedByBraces: Boolean = getLBrace.isDefined

  def isEnclosedByColon: Boolean = getColon.isDefined
}

object ScOptionalBracesOwner {
  object withColon {
    def unapply(elem: ScOptionalBracesOwner): Option[PsiElement] = elem.getColon
  }
}
