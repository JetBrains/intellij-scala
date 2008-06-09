package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi._
import scope._

trait ScDeclarationSequenceHolder extends ScalaPsiElement {
  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    if (lastParent != null) {
      var run = if (this == lastParent.getParent) lastParent.getPrevSibling else getLastChild 
      while (run != null) {
        if (!run.processDeclarations(processor, state, lastParent, place)) return false
        run = run.getPrevSibling
      }
    }
    true
  }
}