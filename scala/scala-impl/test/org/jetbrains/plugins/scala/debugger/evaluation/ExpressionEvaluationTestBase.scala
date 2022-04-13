package org.jetbrains.plugins.scala
package debugger
package evaluation

import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, EvaluateException}
import com.intellij.debugger.engine.{DebuggerUtils, SuspendContextImpl}
import org.junit.Assert.fail

abstract class ExpressionEvaluationTestBase extends NewScalaDebuggerTestCase {

  private var onBreakpointActionsIterator: Iterator[SuspendContextImpl => Unit] = _

  override protected def tearDown(): Unit = {
    try {
      if (onBreakpointActionsIterator.hasNext) {
        fail(s"The debugger did not stop to execute all actions. Remaining: ${onBreakpointActionsIterator.size}")
      }
    } finally {
      super.tearDown()
    }
  }

  protected def expressionEvaluationTest(mainClass: String = getTestName(false))
                                        (actions: (SuspendContextImpl => Unit)*): Unit = {
    onBreakpointActionsIterator = actions.iterator
    createLocalProcess(mainClass)

    onBreakpoints { ctx =>
      if (onBreakpointActionsIterator.hasNext) {
        val action = onBreakpointActionsIterator.next()
        action(ctx)
      }
      resume(ctx)
    }
  }

  protected def evalEquals(expression: String, expected: String)(implicit context: SuspendContextImpl): Unit = {
    try {
      val v = evaluate(CodeFragmentKind.EXPRESSION, expression, context)
      val actual = DebuggerUtils.getValueAsString(createEvaluationContext(context), v)
      assertEquals(expected, actual)
    } catch {
      case e: EvaluateException => fail(e.getMessage)
    }
  }

  protected def evalStartsWith(expression: String, expected: String)(implicit context: SuspendContextImpl): Unit = {
    try {
      val v = evaluate(CodeFragmentKind.EXPRESSION, expression, context)
      val actual = DebuggerUtils.getValueAsString(createEvaluationContext(context), v)
      if (!actual.startsWith(expected)) {
        fail(s"$actual does not start with $expected")
      }
    } catch {
      case e: EvaluateException => fail(e.getMessage)
    }
  }

  protected def failing(assertion: => Unit): Unit = {
    var passed = false
    try {
      assertion
      passed = true
    } catch {
      case _: AssertionError =>
    }

    if (passed) {
      fail("Assertion passed but was supposed to fail")
    }
  }
}
