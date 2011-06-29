package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState, PsiPackage}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */
trait ScPackage extends ScPackageLike with PsiPackage {
  def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                          lastParent: PsiElement, place: PsiElement, lite: Boolean): Boolean
}