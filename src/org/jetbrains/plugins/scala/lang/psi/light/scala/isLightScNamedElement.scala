package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * Nikolay.Tropin
 * 2014-08-15
 */
object isLightScNamedElement {
  def unapply(lightElem: LightElement): Option[ScNamedElement] = lightElem match {
    case light: ScLightBindingPattern => Some(light.b)
    case light: ScLightFieldId => Some(light.f)
    case light: ScLightFunctionDeclaration => Some(light.fun)
    case light: ScLightFunctionDefinition => Some(light.fun)
    case light: ScLightParameter => Some(light.param)
    case light: ScLightTypeAliasDeclaration => Some(light.ta)
    case light: ScLightTypeAliasDefinition => Some(light.ta)
    case _ => None
  }
}