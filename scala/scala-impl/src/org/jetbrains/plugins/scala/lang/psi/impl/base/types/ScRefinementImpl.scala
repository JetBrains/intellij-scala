package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScRefinementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScRefinement{
  override def toString: String = "Refinement"

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    val iterator = types.iterator
    while (iterator.hasNext) {
      val elem = iterator.next()
      if (!processor.execute(elem, state)) return false
    }

    val iterator1 = holders.iterator.flatMap(_.declaredElements.iterator)
    while (iterator1.hasNext) {
      val elem = iterator1.next()
      if (!processor.execute(elem, state)) return false
    }
    true
  }
}