package org.jetbrains.plugins.scala
package lang.psi.light

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.light.LightElement

/**
 * Nikolay.Tropin
 * 2014-08-15
 */
object isWrapper {
  def unapply(lightElem: LightElement): Option[PsiNamedElement] = lightElem match {
    case PsiClassWrapper(definition) => Some(definition)
    case PsiTypedDefinitionWrapper(delegate) => Some(delegate)
    case ScFunctionWrapper(delegate) => Some(delegate)
    case StaticPsiMethodWrapper(method) => Some(method)
    case StaticPsiTypedDefinitionWrapper(delegate) => Some(delegate)
    case StaticTraitScFunctionWrapper(function) => Some(function)
    case _ => None
  }
}
