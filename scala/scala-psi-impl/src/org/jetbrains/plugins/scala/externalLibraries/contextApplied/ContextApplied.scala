package org.jetbrains.plugins.scala.externalLibraries.contextApplied

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

object ContextApplied {
  trait SyntheticElementsOwner extends ScalaPsiElement {
    def syntheticContextAppliedDefs: Seq[ScalaPsiElement]

    override def processDeclarations(
      processor:  PsiScopeProcessor,
      state:      ResolveState,
      lastParent: PsiElement,
      place:      PsiElement
    ): Boolean = syntheticContextAppliedDefs.forall(processor.execute(_, state))
  }
}
