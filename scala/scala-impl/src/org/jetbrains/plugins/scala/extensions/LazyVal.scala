package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

object LazyVal {
  def unapply(pd: ScPatternDefinition): Option[ScPatternDefinition] = {
    if (pd.hasModifierProperty("lazy")) Some(pd)
    else None
  }
}
