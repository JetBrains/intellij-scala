package org.jetbrains.plugins.scala.structuralSearch.filter

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

class TypeElementFilter extends NodeFilter {
    override def accepts(element: PsiElement): Boolean = element.is[ScTypeElement]
  }
