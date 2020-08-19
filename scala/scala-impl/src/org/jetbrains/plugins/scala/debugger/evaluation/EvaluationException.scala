package org.jetbrains.plugins.scala
package debugger.evaluation

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluateExceptionUtil}
import org.jetbrains.annotations.Nls

/**
 * Nikolay.Tropin
 * 2014-09-28
 */
object EvaluationException {
  def apply(@Nls message: String): EvaluateException = EvaluateExceptionUtil.createEvaluateException(message)
  def apply(thr: Throwable): EvaluateException = EvaluateExceptionUtil.createEvaluateException(thr)
  def apply(@Nls message: String, thr: Throwable): EvaluateException = EvaluateExceptionUtil.createEvaluateException(message, thr)
}
