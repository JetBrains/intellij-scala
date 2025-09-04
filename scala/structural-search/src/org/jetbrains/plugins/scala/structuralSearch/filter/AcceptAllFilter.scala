package org.jetbrains.plugins.scala.structuralSearch.filter

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}

class AcceptAllFilter extends NodeFilter {
  // accepts all elements, used for example for matching variables
  // matching visitor decides which element is allowed
  override def accepts(element: PsiElement): Boolean =
    element.getLanguage == ScalaLanguage.INSTANCE || element.getLanguage == Scala3Language.INSTANCE
}
