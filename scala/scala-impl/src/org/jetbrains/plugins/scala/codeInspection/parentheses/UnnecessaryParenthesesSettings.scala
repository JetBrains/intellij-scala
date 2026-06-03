package org.jetbrains.plugins.scala.codeInspection.parentheses

case class UnnecessaryParenthesesSettings(ignoreClarifying: Boolean,
                                          ignoreAroundFunctionType: Boolean,
                                          ignoreAroundFunctionTypeParam: Boolean,
                                          ignoreAroundFunctionExprParam: Boolean)

object UnnecessaryParenthesesSettings {
  val default: UnnecessaryParenthesesSettings = UnnecessaryParenthesesSettings(
    ignoreClarifying = true,
    ignoreAroundFunctionType = false,
    ignoreAroundFunctionTypeParam = false,
    ignoreAroundFunctionExprParam = false
  )
}
