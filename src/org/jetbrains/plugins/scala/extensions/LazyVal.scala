package org.jetbrains.plugins.scala.extensions

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

/**
 * @author Nikolay.Tropin
 */
object LazyVal {
  def unapply(pd: ScPatternDefinition): Option[ScPatternDefinition] = {
    if (pd.hasModifierProperty("lazy")) Some(pd)
    else None
  }
}
