package org.jetbrains.plugins.scala.extensions.implementation

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * User: Alefas
 * Date: 16.02.12
 */
class PsiNamedElementExt(named: PsiNamedElement) {
  def name: String = {
    named match {
      case named: ScNamedElement => named.name
      case named => named.getName
    }
  }
}
