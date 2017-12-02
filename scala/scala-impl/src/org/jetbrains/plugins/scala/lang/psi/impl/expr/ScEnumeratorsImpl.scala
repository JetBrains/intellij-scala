package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.mutable

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScEnumeratorsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnumerators {

  override def toString: String = "Enumerators"

  def enumerators: mutable.WrappedArray[ScEnumerator] = findChildrenByClass[ScEnumerator](classOf[ScEnumerator])

  def generators: mutable.WrappedArray[ScGenerator] = findChildrenByClass[ScGenerator](classOf[ScGenerator])

  def guards: mutable.WrappedArray[ScGuard] = findChildrenByClass[ScGuard](classOf[ScGuard])

  def namings: Seq[ScPatterned] =
    for (c <- getChildren if c.isInstanceOf[ScGenerator] || c.isInstanceOf[ScEnumerator])
          yield c.asInstanceOf[ScPatterned]

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val reverseChildren = getChildren.reverse
    val children =
      if (reverseChildren.contains(lastParent)) reverseChildren.drop(reverseChildren.indexOf(lastParent) + (
         lastParent match {
           case _: ScGenerator => 1
           case _ => 0
         }
        ))
      else reverseChildren
    for (c <- children) {
      c match {
        case c: ScGenerator =>
          for (p <- Option(c.pattern); b <- p.bindings) if (!processor.execute(b, state)) return false
          processor match {
            case b: BaseProcessor => b.changedLevel
            case _ =>
          }
        case c: ScEnumerator =>
          for (p <- Option(c.pattern); b <- p.bindings) if (!processor.execute(b, state)) return false
        case _ =>
      }
    }
    true
  }

  override def patterns: Seq[ScPattern] = namings.reverse.flatMap(_.pattern.toOption)
}
