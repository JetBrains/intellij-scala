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
    case wr: PsiClassWrapper => Some(wr.definition)
    case wr: PsiTypedDefinitionWrapper => Some(wr.delegate)
    case ScFunctionWrapper(delegate) => Some(delegate)
    case wr: StaticPsiMethodWrapper => Some(wr.method)
    case wr: StaticPsiTypedDefinitionWrapper => Some(wr.delegate)
    case wr: StaticTraitScFunctionWrapper => Some(wr.function)
    case _ => None
  }
}
