package org.jetbrains.plugins.scala
package debugger.evaluation

import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil

/**
 * Nikolay.Tropin
 * 2014-09-28
 */
object EvaluationException {
  def apply(message: String) = EvaluateExceptionUtil.createEvaluateException(message)
  def apply(thr: Throwable) = EvaluateExceptionUtil.createEvaluateException(thr)
  def apply(message: String, thr: Throwable) = EvaluateExceptionUtil.createEvaluateException(message, thr)
}
