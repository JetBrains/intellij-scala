package org.jetbrains.plugins.scala
package lang
package completion
package ml

import com.intellij.codeInsight.completion.ml.MLFeatureValue
import org.junit.Assert.assertEquals

private[ml] object AssertFeatureValues {

  // no equal impl for MLFeatureValue
  private[this] object DoubleValue {

    private[this] val FloatPattern = """FloatValue\(value=([-\d.]+)\)""".r

    def unapply(value: MLFeatureValue): Option[Double] = value.toString match {
      case FloatPattern(doubleValue) => Some(doubleValue.toDouble)
      case _ => None
    }
  }

  def equals(expected: MLFeatureValue, actual: MLFeatureValue): Unit =
    (expected, actual) match {
      case (DoubleValue(floatExpected), DoubleValue(floatActual)) => assertEquals(floatExpected, floatActual, 0.001)
      case _ => assertEquals(expected, actual)
    }
}
