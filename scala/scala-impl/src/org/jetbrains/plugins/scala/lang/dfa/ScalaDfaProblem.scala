package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

sealed trait ScalaDfaProblem extends UnsatisfiedConditionProblem
case class ScalaCastProblem(castExpression: ScExpression, castType: ScExpression) extends ScalaDfaProblem