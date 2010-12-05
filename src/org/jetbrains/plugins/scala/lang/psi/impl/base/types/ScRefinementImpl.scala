package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.base.types._
import com.intellij.psi.{PsiElement, ResolveState}
import com.intellij.psi.scope.PsiScopeProcessor
import api.statements.ScDeclaredElementsHolder

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScRefinementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScRefinement{
  override def toString: String = "Refinement"

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    val iterator = types.iterator
    while (iterator.hasNext) {
      val elem = iterator.next
      if (!processor.execute(elem, state)) return false
    }

    val iterator1 = holders.iterator.flatMap(_.declaredElements.iterator)
    while (iterator1.hasNext) {
      val elem = iterator1.next
      if (!processor.execute(elem, state)) return false
    }
    true
  }
}