package org.jetbrains.plugins.scala.components.libextensions.api.psi

import org.jetbrains.plugins.scala.lang.macros.evaluator.ScalaMacroTypeable
import org.jetbrains.plugins.scala.lang.macros.evaluator.ScalaMacroEvaluator.MacroImpl

trait MacroTypeContributor {
  def getTypingRules: Map[MacroImpl, ScalaMacroTypeable]
}
