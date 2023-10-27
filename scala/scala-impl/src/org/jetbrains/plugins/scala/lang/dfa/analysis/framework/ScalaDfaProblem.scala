package org.jetbrains.plugins.scala.lang.dfa.analysis.framework

import com.intellij.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

sealed trait ScalaDfaProblem extends UnsatisfiedConditionProblem

case class ScalaCollectionAccessProblem(lengthDescriptor: DerivedVariableDescriptor,
                                        accessExpression: ScExpression,
                                        exceptionName: String)
  extends ScalaDfaProblem with IndexOutOfBoundsProblem {
  override def getLengthDescriptor: DerivedVariableDescriptor = lengthDescriptor
}

case class ScalaNullAccessProblem(accessExpression: ScExpression) extends ScalaDfaProblem
