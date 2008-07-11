package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import psi.ScalaPsiElementImpl
import api.toplevel.packaging._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScPackagingImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPackaging {
  override def toString = "ScPackaging"

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (inner <- getTopStatements) {
      if (!processor.execute(inner, state)) return false
    }
    true
  }
}
