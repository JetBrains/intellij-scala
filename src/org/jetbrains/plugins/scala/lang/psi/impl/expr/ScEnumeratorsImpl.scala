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

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScEnumeratorsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnumerators {

  override def toString: String = "Enumerators"

  def enumerators = findChildrenByClass[ScEnumerator](classOf[ScEnumerator])

  def generators = findChildrenByClass[ScGenerator](classOf[ScGenerator])

  def guards = findChildrenByClass[ScGuard](classOf[ScGuard])

  def namings = for (c <- getChildren if c.isInstanceOf[ScGenerator] || c.isInstanceOf[ScEnumerator])
          yield c.asInstanceOf[{def pattern: ScPattern}]

  type Patterned = {
    def pattern: ScPattern
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val ns = namings.reverse
    val begin = if (ns.contains(lastParent)) ns.drop(ns.indexOf(lastParent)) else ns
    val patts = begin.map((ns: Patterned) => ns.pattern).filter(_ != null)    
    val binds = patts.flatMap((p: ScPattern) => p.bindings)
    for (b <- binds) if (!processor.execute(b, state)) return false
    true
  }

}