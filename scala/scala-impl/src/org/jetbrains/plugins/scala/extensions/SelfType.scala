package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * Pavel Fatin
 */

object SelfType {
  def unapply(definition: ScTemplateDefinition) = Some(definition.selfType)
}
