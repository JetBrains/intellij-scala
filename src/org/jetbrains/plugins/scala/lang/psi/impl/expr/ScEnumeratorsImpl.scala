package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.psi.scope._
import lang.resolve.processor.BaseProcessor

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScEnumeratorsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnumerators {

  override def toString: String = "Enumerators"

  def enumerators = findChildrenByClass[ScEnumerator](classOf[ScEnumerator])

  def generators = findChildrenByClass[ScGenerator](classOf[ScGenerator])

  def guards = findChildrenByClass[ScGuard](classOf[ScGuard])

  def namings: Seq[ScPatterned] =
    for (c <- getChildren if c.isInstanceOf[ScGenerator] || c.isInstanceOf[ScEnumerator])
          yield c.asInstanceOf[ScPatterned]

  type Patterned = {
    def pattern: ScPattern
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val reverseChildren = getChildren.reverse
    val children =
      if (reverseChildren.contains(lastParent)) reverseChildren.drop(reverseChildren.indexOf(lastParent) + (
         lastParent match {
           case g: ScGenerator => 1
           case _ => 0
         }
        ))
      else reverseChildren
    for (c <- children) {
      c match {
        case c: ScGenerator =>
          for (b <- c.pattern.bindings) if (!processor.execute(b, state)) return false
          processor match {
            case b: BaseProcessor => b.changedLevel
            case _ =>
          }
        case c: ScEnumerator =>
          for (b <- c.pattern.bindings) if (!processor.execute(b, state)) return false
        case _ =>
      }
    }
    true
  }

}