package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi._

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
      }
      case _ => true
    }
  }
}