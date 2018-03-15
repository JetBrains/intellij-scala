package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** Member of one one the expression, type, or pattern trees.
  * This type allows to write generic traits implemented
  * in the three trees, eg [[ScGenericParenthesisedNode]] or
  * [[ScGenericInfixNode]].
  *
  * @author Clément Fournier
  */
trait TreeMember[E <: ScalaPsiElement] extends ScalaPsiElement {

  /** Returns an optional containing the given node, which is
    * empty if the node is not part of the same tree.
    */
  def asSameTree(p: PsiElement): Option[TreeMember[E]] = if (isSameTree(p)) Some(p.asInstanceOf[TreeMember[E]]) else None

  def isSameTree(p: PsiElement): Boolean

}
