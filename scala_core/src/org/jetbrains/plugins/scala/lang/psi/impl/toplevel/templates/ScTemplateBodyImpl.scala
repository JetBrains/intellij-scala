package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import com.intellij.psi.{PsiElement, ResolveState}
import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:04
*/

class ScTemplateBodyImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTemplateBody {
  override def toString: String = "ScTemplateBody"

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    if (lastParent != null) {
      var run = lastParent.getPrevSibling
      while (run != null) {
        if (run.isInstanceOf[ScImportStmt] &&
            !run.processDeclarations(processor, state, lastParent, place)) return false
        run = run.getPrevSibling
      }
    }
    true
  }
}