package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScFunctionalTypeElement

import scala.annotation.tailrec

/** Generic type for parenthesised nodes.
  *
  * @author Clément Fournier
  */
trait ScParenthesizedElement extends ScalaPsiElement {
  /** Expression, type element or pattern */
  type Kind <: ScalaPsiElement

  /** Returns the expression, type or pattern that is contained
    * within those parentheses.
    */
  def innerElement: Option[Kind]

  /** Returns true if the parentheses represented by this node are clarifying,
    * ie they clarify precedence between operators that have different precedences.
    */
  def isParenthesisClarifying: Boolean

  /** Checks that p is of same kind, have to be defined in inheritors because of erasure */
  protected def isSameTree(p: PsiElement): Boolean

  /** Returns true if these parentheses are nested within other parentheses. */
  def isNestedParenthesis: Boolean = isSameTree(getParent) && getParent.isInstanceOf[ScParenthesizedElement]

  /** Returns true if these parentheses are directly enclosing other parentheses. */
  def isNestingParenthesis: Boolean = innerElement.exists(_.isInstanceOf[ScParenthesizedElement])

  /** Gets the precedence of the tree member. Lower int value is applied first (higher precedence).
    * The highest precedence is 0. Nodes with precedence 0 are indivisible.
    */
  protected def getPrecedence(e: Kind): Int

  /** Returns true if parentheses around the node are always redundant. */
  protected def isIndivisible(e: Kind): Boolean = getPrecedence(e) == 0

  /** Returns true if the parenthesised can be stripped safely.
    *
    * @param ignoreClarifying Ignore clarifying parentheses ([[isParenthesisClarifying]])
    */
  def isParenthesisRedundant(ignoreClarifying: Boolean = false): Boolean =
    !isParenthesisNeeded(ignoreClarifying)

  /** Returns true if the parentheses represented by this node cannot be stripped
    * without changing the semantics of the AST.
    *
    * @param ignoreClarifying Whether to consider clarifying parentheses as required
    * @return True if this node
    */
  def isParenthesisNeeded(ignoreClarifying: Boolean): Boolean =
    (ignoreClarifying && isParenthesisClarifying) || isParenthesisNeeded

  def isParenthesisNeeded: Boolean = {
    val This = this

    if (innerElement.isEmpty) true
    else if (!isSameTree(getParent)) false
    else (getParent.asInstanceOf[Kind], innerElement.get) match {
      case (_, c) if isIndivisible(c) => false

      case (p, c) if getPrecedence(p) < getPrecedence(c) => true
      case (p, c) if getPrecedence(p) > getPrecedence(c) => false

      // Infix chain with same precedence:
      // - If the two operators have different associativities, then the parentheses are required
      // - If they have the same associativity, then right- or left- associativity applies depending on the operator
      case (p: ScInfixElement, c: ScInfixElement) if p.isLeftAssoc != c.isLeftAssoc => true

      case (ifx @ ScInfixElement(_, _, Some(This)), _: ScInfixElement) => ifx.isLeftAssoc
      case (ifx @ ScInfixElement(This, _, _), _: ScInfixElement) => ifx.isRightAssoc

      // Function types are right associative, ie A => (B => C) === A => B => C
      case (ScFunctionalTypeElement(This, _), _: ScFunctionalTypeElement) => true

      case _ => false
    }
  }

  @tailrec
  private def getInnermostNonParen(node: ScalaPsiElement): ScalaPsiElement = node match {
    // since we go down, it can only be the same tree
    case ScParenthesizedElement(inner) => getInnermostNonParen(inner)
    case _ => node
  }

  def getTextOfStripped(ignoreClarifying: Boolean = false): String = {
    getInnermostNonParen(this) match {
      case paren @ ScParenthesizedElement(_) => paren.getText
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
  def stripParentheses(keepParentheses: Boolean = false): Kind = {

    def replaceWithNode(elt: PsiElement): Kind = this.replace(elt).asInstanceOf[Kind]

    getInnermostNonParen(this) match {
      case elt if elt eq this => this.asInstanceOf[Kind]
      case elt: ScParenthesizedElement if keepParentheses => replaceWithNode(elt)
      case elt if keepParentheses => replaceWithNode(elt.getParent)
      case elt => replaceWithNode(elt)
    }
  }
}


object ScParenthesizedElement {
  def unapply(p: ScParenthesizedElement): Option[p.Kind] = p.innerElement
}