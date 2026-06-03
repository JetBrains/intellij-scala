package org.jetbrains.plugins.scala.structuralSearch.filter

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable

class ValVarFilter extends NodeFilter {
    // add all elements that could be a function call
    override def accepts(element: PsiElement): Boolean = element.is[ScValueOrVariable]
  }
