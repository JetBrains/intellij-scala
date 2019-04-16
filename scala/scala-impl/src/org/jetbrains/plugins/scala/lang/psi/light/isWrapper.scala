package org.jetbrains.plugins.scala
package lang.psi.light

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.extensions.ObjectExt

/**
 * Nikolay.Tropin
 * 2014-08-15
 */
object isWrapper {
  def unapply(lightElem: LightElement): Option[PsiNamedElement] = lightElem match {
    case PsiClassWrapper(definition) => Some(definition)
    case methodWrapper: PsiMethodWrapper[_] => methodWrapper.delegate.asOptionOf[PsiNamedElement]
    case _ => None
  }
}
