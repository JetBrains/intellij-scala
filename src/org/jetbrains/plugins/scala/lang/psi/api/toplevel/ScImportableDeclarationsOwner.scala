package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{ResolveState, PsiElement}
import types.ScSubstitutor
import types.result.{Success, TypingContext}
import resolve.processor.BaseProcessor

/**
 * @author ilyas
 */

trait ScImportableDeclarationsOwner extends ScalaPsiElement {
  self: ScTypedDefinition =>

  /**
   * Declarations may be taken from stable elements only
   */
  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement) =
    if (isStable) {
      ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, getType(TypingContext.empty))
    } else true

}