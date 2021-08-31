package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

sealed trait ScalaDfaAnchor extends DfaAnchor
case class ScalaExpressionAnchor(expression: ScExpression) extends ScalaDfaAnchor
