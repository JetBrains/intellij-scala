package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import api.base.patterns._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import com.intellij.psi._
import scope.PsiScopeProcessor

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScCaseClauseImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScCaseClause{
  override def toString: String = "CaseClause"
  
  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {

    pattern match {
      case Some(p) => {
        val iterator = p.bindings.iterator
        while (iterator.hasNext) {
          val b = iterator.next
          if (!processor.execute(b, state)) return false
        }
        true
      }
      case _ => true
    }
  }
}