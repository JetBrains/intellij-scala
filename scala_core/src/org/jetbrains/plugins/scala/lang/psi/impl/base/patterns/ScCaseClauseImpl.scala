package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

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
        expr match {
          case Some(e) if e == lastParent =>
            for (b <- p.bindings) {
              if (!processor.execute(b, state)) return false
            }
            true
          case _ => true
        }
        guard match {
          case Some(g) if g == lastParent =>
            for (b <- p.bindings) {
              if (!processor.execute(b, state)) return false
            }
            true
          case _ => true
        }
      }
      case _ => true
    }
  }
}