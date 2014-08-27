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
    case wr: PsiTypedDefinitionWrapper => Some(wr.typedDefinition)
    case wr: ScFunctionWrapper => Some(wr.function)
    case wr: StaticPsiMethodWrapper => Some(wr.method)
    case wr: StaticPsiTypedDefinitionWrapper => Some(wr.typedDefinition)
    case wr: StaticTraitScFunctionWrapper => Some(wr.function)
    case _ => None
  }
}
