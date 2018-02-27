package org.jetbrains.plugins.scala
package codeInsight
package hints
package hintTypes

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

private[hints] case object ReturnTypeHintType extends HintType(
  defaultValue = true,
  idSegments = "function", "return", "type"
) {

  override protected val delegate: HintFunction = {
    case function: ScFunction if !function.hasExplicitType =>
      function.returnType.toSeq
        .map(InlayInfo(_, function.parameterList))
  }
}
