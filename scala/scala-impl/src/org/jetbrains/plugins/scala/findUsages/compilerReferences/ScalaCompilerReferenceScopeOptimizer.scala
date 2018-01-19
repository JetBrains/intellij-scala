package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.psi.PsiElement
import com.intellij.psi.search.{GlobalSearchScope, ScopeOptimizer}

class ScalaCompilerReferenceScopeOptimizer extends ScopeOptimizer {
  override def getScopeToExclude(element: PsiElement): GlobalSearchScope =
    ScalaCompilerReferenceService.getInstance(element.getProject).getScopeWithoutCodeReferences(element)
}
