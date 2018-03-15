package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScGenericParenthesisedNode.AnyParenthesisedNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScParenthesisedPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParenthesisedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr

import scala.annotation.tailrec

/** Generic type for parenthesised nodes.
  *
  * @author Clément Fournier
  */
trait ScGenericParenthesisedNode[E <: ScalaPsiElement] extends TreeMember[E] {

  /** Returns the expression, type or pattern that is contained
    * within those parentheses.
    */
  def subNode: Option[TreeMember[E]]

  /** Returns true if the parentheses represented by this node are clarifying,
    * ie they clarify precedence between operators that have different precedences.
    */
  def isParenthesisClarifying: Boolean


  /** Returns true if these parentheses are nested within other parentheses. */
  def isNestedParenthesis: Boolean = asSameTree(getParent).exists(_.isInstanceOf[AnyParenthesisedNode])

  /** Returns true if these parentheses are directly enclosing other parentheses. */
  def isNestingParenthesis: Boolean = subNode.exists(_.isInstanceOf[AnyParenthesisedNode])

  /** Gets the precedence of the tree member. Lower int value is applied first (higher precedence).
    * The highest precedence is 0. Nodes with precedence 0 are indivisible.
    */
  protected def getPrecedence(e: TreeMember[E]): Int

  /** Returns true if parentheses around the node are always redundant. */
  protected def isIndivisible(e: TreeMember[E]): Boolean = getPrecedence(e) == 0

  /** Returns true if the parenthesised can be stripped safely.
    *
    * @param ignoreClarifying Ignore clarifying parentheses ([[isParenthesisClarifying]])
    */
  def isParenthesisRedundant(ignoreClarifying: Boolean = false): Boolean
  = !isParenthesisNeeded(ignoreClarifying)

  /** Returns true if the parentheses represented by this node cannot be stripped
    * without changing the semantics of the AST.
    *
    * @param ignoreClarifying Whether to consider clarifying parentheses as required
    * @return True if this node
    */
  def isParenthesisNeeded(ignoreClarifying: Boolean): Boolean
  = (ignoreClarifying && isParenthesisClarifying) || isParenthesisNeeded

  def isParenthesisNeeded: Boolean = {
    val This = this

    if (subNode.isEmpty) true
    else (getParent, subNode.get) match {
      case (p, _) if !this.isSameTree(p) => false
      case (_, c) if isIndivisible(c) => false

      case (p: TreeMember[E@unchecked], c) if getPrecedence(p) < getPrecedence(c) => true
      case (p: TreeMember[E@unchecked], c) if getPrecedence(p) > getPrecedence(c) => false

      // Infix chain with same precedence:
      // - If the two operators have different associativities, then the parentheses are required
      // - If they have the same associativity, then right- or left- associativity applies depending on the operator
      case (p: ScGenericInfixNode[E@unchecked], c: ScGenericInfixNode[E@unchecked]) if p.associativity != c.associativity => true

      case (ifx @ ScGenericInfixNode(_, _, Some(This)), _: ScGenericInfixNode[E@unchecked]) => ifx.isLeftAssoc
      case (ifx @ ScGenericInfixNode(This, _, _), _: ScGenericInfixNode[E@unchecked]) => ifx.isRightAssoc

      case _ => false
    }
  }

  @tailrec
  private def getInnermostNonParen(node: TreeMember[E]): TreeMember[E] = node match {
    // since we go down, it can only be the same tree
    case ScGenericParenthesisedNode(inner: TreeMember[E]@unchecked) => getInnermostNonParen(inner)
    case _: E@unchecked => node
  }

  def getTextOfStripped(ignoreClarifying: Boolean = false): String = {
    getInnermostNonParen(this) match {
      case paren @ ScGenericParenthesisedNode(_) => paren.getText
      case elt if ignoreClarifying => elt.getParent.getText
      case elt => elt.getText
    }
  }


  /** Strips parentheses from this element downwards. This node is replaced by one
    * of its subpatterns in the children of the parent node. If the
    * `keepParentheses` parameter is true, then one level of nesting is kept.
    * Otherwise, this node is replaced by its first non-parenthesized descendant.
    *
    * This method may change the semantics of the AST if the pattern
    * needed the parentheses. Use [[isParenthesisNeeded]] to ensure the parentheses aren't needed.
    *
    * @param keepParentheses Whether to keep one level of parentheses or not
    * @return The node which replaces this node. If no parentheses were
    *         found to remove, returns `this`, unmodified
    */
  def stripParentheses(keepParentheses: Boolean = false): TreeMember[E] = {

    def replaceWithNode(elt: TreeMember[E]): TreeMember[E] = {
      val parentNode = getParent.getNode
      val newNode = elt.copy.getNode
      parentNode.replaceChild(this.getNode, newNode)
      this.asSameTree(newNode.getPsi).get
    }

    getInnermostNonParen(this) match {
      case elt if elt eq this => this
      case elt: ScGenericParenthesisedNode[E] if keepParentheses => replaceWithNode(elt)
      case elt if keepParentheses => replaceWithNode(this.asSameTree(elt.getParent).get)
      case elt => replaceWithNode(elt)
    }
  }
}


object ScGenericParenthesisedNode {

  type Parenthesised[T <: ScalaPsiElement] = ScGenericParenthesisedNode[T]
  type AnyParenthesisedNode = Parenthesised[_ <: ScalaPsiElement]

  def unapply(arg: ScGenericParenthesisedNode[_]): Option[TreeMember[_]] = arg match {
    case p: ScParenthesisedPattern => ScParenthesisedPattern unapply p
    case t: ScParenthesisedTypeElement => ScParenthesisedTypeElement unapply t
    case e: ScParenthesisedExpr => ScParenthesisedExpr unapply e
    case _ => None
  }
}