package org.jetbrains.plugins.scala
package debugger
package evaluation

import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, EvaluateException}
import com.intellij.debugger.engine.{DebuggerUtils, SuspendContextImpl}
import com.sun.jdi.VoidValue
import org.junit.Assert.{assertTrue, fail}

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
    assertTrue("Test should execute an action on at least one breakpoint", actions.nonEmpty)

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
      val actual = evaluateExpressionToString(expression)
      assertEquals(expected, actual)
    } catch {
      case e: EvaluateException => fail(e.getMessage)
    }
  }

  protected def evalStartsWith(expression: String, expected: String)(implicit context: SuspendContextImpl): Unit = {
    try {
      val actual = evaluateExpressionToString(expression)
      if (!actual.startsWith(expected)) {
        fail(s"$actual does not start with $expected")
      }
    } catch {
      case e: EvaluateException => fail(e.getMessage)
    }
  }

  protected def evalFailsWith(expression: String, message: String)(implicit context: SuspendContextImpl): Unit = {
    try {
      evaluateExpressionToString(expression)
      fail(s"Expression $expression was supposed to fail with an EvaluateException, but didn't")
    } catch {
      case e: EvaluateException => assertEquals(message, e.getMessage)
    }
  }

  private def evaluateExpressionToString(expression: String)(implicit context: SuspendContextImpl): String = {
    val kind =
      if (expression.contains(System.lineSeparator())) CodeFragmentKind.CODE_BLOCK
      else CodeFragmentKind.EXPRESSION

    evaluate(kind, expression, context) match {
      case _: VoidValue => "undefined"
      case v => DebuggerUtils.getValueAsString(createEvaluationContext(context), v)
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
