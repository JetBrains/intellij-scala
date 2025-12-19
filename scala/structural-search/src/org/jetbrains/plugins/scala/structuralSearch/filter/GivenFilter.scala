package org.jetbrains.plugins.scala.structuralSearch.filter

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven

class GivenFilter extends NodeFilter {
  override def accepts(element: PsiElement): Boolean = element.is[ScGiven]
}
