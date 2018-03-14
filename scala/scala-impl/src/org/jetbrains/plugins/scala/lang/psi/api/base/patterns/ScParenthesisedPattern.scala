package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.codeInspection.parentheses.ScPatternUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScTypeElement}

import scala.annotation.tailrec

/** 
* @author Alexander Podkhalyuzin
*/

trait ScParenthesisedPattern extends ScPattern {
  def subpattern: Option[ScPattern] = findChild(classOf[ScPattern])


  /** Strips parentheses from this element downwards. This node is replaced by one
    * of its subpatterns in the children of the parent node. If the
    * `keepParentheses` parameter is true, then one level of nesting is kept.
    * Otherwise, this node is replaced by its first non-parenthesized descendant.
    *
    * This method may change the semantics of the AST if the pattern
    * needed the parentheses. Use [[ScPatternUtil.patternNeedsParentheses]]
    * to ensure the parentheses aren't needed.
    *
    * @param keepParentheses Whether to keep one level of parentheses or not
    * @return The node which replaces this node. If no parentheses were
    *         found to remove, returns `this`, unmodified
    */
  def stripParentheses(keepParentheses: Boolean = false): ScPattern = {

    @tailrec
    def getInnermostNonParen(node: ScPattern): ScPattern = node match {
      case ScParenthesisedPattern(inner) => getInnermostNonParen(inner)
      case _: ScPattern => node
    }

    def replaceWithNode(elt: ScPattern): ScPattern = {
      val parentNode = getParent.getNode
      val newNode = elt.copy.getNode
      parentNode.replaceChild(this.getNode, newNode)
      newNode.getPsi.asInstanceOf[ScPattern]
    }

    getInnermostNonParen(this) match {
      case elt if elt eq this => this
      case elt: ScParenthesisedPattern if keepParentheses => replaceWithNode(elt)
      case elt if keepParentheses => replaceWithNode(elt.getParent.asInstanceOf[ScPattern])
      case elt => replaceWithNode(elt)
    }
  }
}




object ScParenthesisedPattern {
  def unapply(e: ScParenthesisedPattern): Option[ScPattern] = e.subpattern
}