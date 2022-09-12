package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScImportableDeclarationsOwner extends ScalaPsiElement {
  self: ScTypedDefinition =>

  /**
   * Declarations may be taken from stable elements only
   */
  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean =
    if (isStable) {
      ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, `type`())
    } else true

}