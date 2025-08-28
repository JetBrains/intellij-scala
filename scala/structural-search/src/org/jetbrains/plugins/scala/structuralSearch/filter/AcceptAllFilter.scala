package org.jetbrains.plugins.scala.structuralSearch.filter

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement

class AcceptAllFilter extends NodeFilter {
  // accepts all elements, used for example for matching variables
  // matching visitor decides which element is allowed
  override def accepts(element: PsiElement): Boolean = true
}
