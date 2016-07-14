package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.{Evaluator, Modifier, TypeEvaluator}
import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}

/**
  * @author Nikolay.Tropin
  */
class ScalaTypeEvaluator(jvmName: JVMName) extends Evaluator {
  override def getModifier: Modifier = null

  override def evaluate(context: EvaluationContextImpl): AnyRef = {
    try {
      new TypeEvaluator(jvmName).evaluate(context)
    }
    catch {
      case e: EvaluateException =>
        val targetExc = e.getExceptionFromTargetVM
        if (targetExc != null && targetExc.referenceType().name == "java.lang.ClassNotFoundException") {
          val debugProcess = context.getDebugProcess
          val name = jvmName.getName(debugProcess)
          if (name.endsWith("$class")) {
            new TypeEvaluator(JVMNameUtil.getJVMRawText(name.stripSuffix("$class"))).evaluate(context)
          }
          else throw e
        }
        else throw e
    }
  }
}
