package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * @author Alexander Podkhalyuzin
 * Date: 07.03.2008
 */

class ScExistentialClauseImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExistentialClause {
  override def toString: String = "ExistentialClause"


  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (lastParent != null) {
      var run = lastParent
      while (run != null) {
        if (!processElement(run, processor, state)) return false
        run = run.getPrevSibling
      }
    }
    true
  }

  private def processElement(e: PsiElement, processor: PsiScopeProcessor, state: ResolveState): Boolean = e match {
    case named: ScNamedElement => processor.execute(named, state)
    case holder: ScDeclaredElementsHolder => {
      for (declared <- holder.declaredElements) {
        if (!processor.execute(declared, state)) return false
      }
      true
    }
    case _ => true
  }

}