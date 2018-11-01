package org.jetbrains.plugins.scala
package lang.psi.light.scala

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * Nikolay.Tropin
 * 2014-08-15
 */
object isLightScNamedElement {

  def unapply(lightElement: LightElement): Option[ScNamedElement] =
    ScLightElement.unapply(lightElement).orElse {
      lightElement match {
        case light: ScLightFieldId => Some(light.f)
        case _ => None
      }
    }
}