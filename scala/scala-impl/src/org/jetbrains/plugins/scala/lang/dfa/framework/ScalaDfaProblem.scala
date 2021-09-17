package org.jetbrains.plugins.scala.lang.dfa.framework

import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

sealed trait ScalaDfaProblem extends UnsatisfiedConditionProblem
case class ScalaCastProblem(castExpression: ScExpression, targetType: ScExpression) extends ScalaDfaProblem
