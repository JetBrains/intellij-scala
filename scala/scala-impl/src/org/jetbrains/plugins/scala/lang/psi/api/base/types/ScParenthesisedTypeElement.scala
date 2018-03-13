package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import scala.annotation.tailrec

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScParenthesisedTypeElement extends ScTypeElement {
  override protected val typeName = "TypeInParenthesis"

  def typeElement: Option[ScTypeElement] = findChild(classOf[ScTypeElement])


  /** Strips parentheses from this element downwards. This node is replaced by one
    * of its type element descendants in the children of the parent node. If the
    * `keepParentheses` parameter is true, then one level of nesting is kept.
    * Otherwise, this node is replaced by its first non-parenthesized descendant.
    *
    * If this element represents an empty pair of parentheses, like in the function
    * types () => Int or ((())) => Int, then one level of parentheses is kept
    * regardless of `keepParentheses`.
    *
    * This method may change the semantics of the AST if the type
    * needed the parentheses. Use [[ScalaPsiUtil.typeNeedsParentheses]]
    * to ensure the parentheses aren't needed.
    *
    * @param keepParentheses Whether to keep one level of parentheses or not
    * @return The node which replaces this node. If no parentheses were
    *         found to remove, returns `this`, unmodified
    */
  def stripParentheses(keepParentheses: Boolean = false): ScTypeElement = {

    // returns a ScParenthesisedTypeElement if this is a () or a (())
    @tailrec
    def getInnermostNonParen(node: ScTypeElement): ScTypeElement = node match {
      case ScParenthesisedTypeElement(inner) => getInnermostNonParen(inner)
      case _: ScTypeElement => node
    }

    def replaceWithNode(elt: ScTypeElement): ScTypeElement = {
      val parentNode = getParent.getNode
      val newNode = elt.copy.getNode
      parentNode.replaceChild(this.getNode, newNode)
      newNode.getPsi.asInstanceOf[ScTypeElement]
    }

    getInnermostNonParen(this) match {
      case elt if elt eq this => this
      case elt: ScParenthesisedTypeElement if keepParentheses => replaceWithNode(elt)
      case elt if keepParentheses => replaceWithNode(elt.getParent.asInstanceOf[ScTypeElement])
      case elt => replaceWithNode(elt)
    }
  }
}


object ScParenthesisedTypeElement {
  def unapply(e: ScParenthesisedTypeElement): Option[ScTypeElement] = e.typeElement
}