package org.jetbrains.plugins.scala.debugger.evaluation.modern

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.debugger.evaluation.{ExpressionEvaluationTestBase => TestBase}

abstract class ExpressionEvaluationTestBase extends TestBase {

  override protected def setUp(): Unit = {
    super.setUp()
    setModernEvaluatorRegistryKey(true)
  }

  override protected def tearDown(): Unit = {
    setModernEvaluatorRegistryKey(false)
    super.tearDown()
  }

  private def setModernEvaluatorRegistryKey(enabled: Boolean): Unit = {
    Registry.get("scala.debugger.modern.evaluator.enabled").setValue(enabled)
  }
}
