package org.jetbrains.plugins.scala.codeInspection.parentheses

/**
  * Nikolay.Tropin
  * 28-Apr-18
  */
case class UnnecessaryParenthesesSettings(ignoreClarifying: Boolean,
                                          ignoreAroundFunctionType: Boolean,
                                          ignoreAroundFunctionTypeParam: Boolean,
                                          ignoreAroundFunctionExprParam: Boolean)

object UnnecessaryParenthesesSettings {
  val default = UnnecessaryParenthesesSettings(
    ignoreClarifying = true,
    ignoreAroundFunctionType = false,
    ignoreAroundFunctionTypeParam = false,
    ignoreAroundFunctionExprParam = false
  )
}
