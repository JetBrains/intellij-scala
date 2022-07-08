package org.jetbrains.plugins.scala
package lang.psi.light

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.extensions.ObjectExt

object isWrapper {
  def unapply(lightElem: LightElement): Option[PsiNamedElement] = lightElem match {
    case PsiClassWrapper(definition) => Some(definition)
    case methodWrapper: PsiMethodWrapper[_] => methodWrapper.delegate.asOptionOfUnsafe[PsiNamedElement]
    case _ => None
  }
}
