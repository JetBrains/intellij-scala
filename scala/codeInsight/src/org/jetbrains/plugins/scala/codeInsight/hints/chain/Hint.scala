package org.jetbrains.plugins.scala.codeInsight.hints.chain

import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

private class Hint(val textParts: Seq[Text], val expr: ScExpression)