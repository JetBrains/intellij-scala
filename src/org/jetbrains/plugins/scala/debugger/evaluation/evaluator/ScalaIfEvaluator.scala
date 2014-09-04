package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier}
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}
import com.sun.jdi.BooleanValue

/**
 * User: Alefas
 * Date: 19.10.11
 */

class ScalaIfEvaluator(condition: Evaluator, ifBranch: Evaluator, elseBranch: Option[Evaluator]) extends Evaluator {
  private var modifier: Modifier = null

  def evaluate(context: EvaluationContextImpl): AnyRef = {
    var value: AnyRef = condition.evaluate(context)
    value match {
      case v: BooleanValue =>
        if (v.booleanValue) {
          value = ifBranch.evaluate(context)
          modifier = ifBranch.getModifier
        }
        else {
          elseBranch match {
            case Some(elseBranch) =>
              value = elseBranch.evaluate(context)
              modifier = elseBranch.getModifier
              return value
            case None =>
              modifier = null
          }
        }
      case _ => throw EvaluateExceptionUtil.BOOLEAN_EXPECTED
    }
    
    if (elseBranch == None)
      value = context.getDebugProcess.getVirtualMachineProxy.mirrorOf()

    value
  }

  def getModifier: Modifier = modifier
}