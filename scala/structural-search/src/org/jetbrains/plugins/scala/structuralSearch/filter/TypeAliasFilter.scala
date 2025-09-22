package org.jetbrains.plugins.scala.structuralSearch.filter

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

class TypeAliasFilter extends NodeFilter {
    override def accepts(element: PsiElement): Boolean = element.is[ScTypeAlias]
  }
