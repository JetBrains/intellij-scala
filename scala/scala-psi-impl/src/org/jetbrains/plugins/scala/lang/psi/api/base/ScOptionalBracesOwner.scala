package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{LBRACE_OR_COLON_TOKEN_SET, tCOLON, tLBRACE, tRBRACE}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner.rBraceTokenSet

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

  def getRBrace: Option[PsiElement] =
    getNode.getChildren(rBraceTokenSet) match {
      case Array(node) => Option(node.getPsi)
      case _ => None
    }

  @inline def getColon: Option[PsiElement] =
    getEnclosingStartElement.filter(_.elementType == tCOLON)

  def isEnclosedByBraces: Boolean = getLBrace.isDefined

  def isEnclosedByColon: Boolean = getColon.isDefined
}

object ScOptionalBracesOwner {
  object withColon {
    def unapply(elem: ScOptionalBracesOwner): Option[PsiElement] = elem.getColon
  }

  private val rBraceTokenSet = TokenSet.create(tRBRACE)
}
