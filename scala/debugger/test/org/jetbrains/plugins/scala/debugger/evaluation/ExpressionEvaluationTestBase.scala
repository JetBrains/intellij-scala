package org.jetbrains.plugins.scala
package debugger
package evaluation

import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.engine.{DebuggerUtils, SuspendContextImpl}
import com.sun.jdi.{ArrayReference, Value, VoidValue}
import org.junit.Assert.{assertTrue, fail}
import org.junit.experimental.categories.Category

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Collectors
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

@Category(Array(classOf[DebuggerEvaluationTests]))
abstract class ExpressionEvaluationTestBase extends ScalaDebuggerTestCase {

  private val onBreakpointActionsQueue: ConcurrentLinkedQueue[SuspendContextImpl => Unit] = new ConcurrentLinkedQueue()

  override protected def tearDown(): Unit = {
    try {
      if (!onBreakpointActionsQueue.isEmpty) {
        val remaining = onBreakpointActionsQueue.stream().collect(Collectors.toList[SuspendContextImpl => Unit]).asScala.toList
        fail(s"The debugger did not stop to execute all actions. Remaining: $remaining")
      }
    } finally {
      super.tearDown()
    }
  }

  protected def expressionEvaluationTest(mainClass: String = getTestName(false))
                                        (actions: (SuspendContextImpl => Unit)*): Unit = {
    assertTrue("Test should execute an action on at least one breakpoint", actions.nonEmpty)

    onBreakpointActionsQueue.addAll(actions.asJava)
    createLocalProcess(mainClass)

    onEveryBreakpoint { ctx =>
      Option(onBreakpointActionsQueue.poll()).foreach(_ (ctx))
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

  /**
   * Asserts that evaluating `expression` terminates: it either produces a value or reports a
   * clean [[EvaluateException]]. Use for regressions where the concern is that evaluator
   * construction might not terminate (hang or crash) rather than the exact result, which may
   * differ across Scala versions.
   */
  protected def evalCompletes(expression: String)(implicit context: SuspendContextImpl): Unit = {
    try {
      evaluateExpressionToString(expression)
    } catch {
      case _: EvaluateException => // evaluation completed with a clean error, which is fine here
    }
  }

  protected def evalFailsWith(expression: String, message: String)(implicit context: SuspendContextImpl): Unit = {
    assertEvalFailsWith(expression, message)(assertStartsWith)
  }

  protected def evalFailsWithContains(expression: String, message: String)(implicit context: SuspendContextImpl): Unit = {
    val assertContains = { (expected: String, actual: String) =>
      if (!actual.contains(expected)) {
        fail(s"$actual does not contain $expected")
      }
    }
    assertEvalFailsWith(expression, message)(assertContains)
  }

  private def assertEvalFailsWith(expression: String, message: String)(assertion: (String, String) => Unit)(implicit context: SuspendContextImpl): Unit = {
    try {
      evaluateExpressionToString(expression)
      fail(s"Expression $expression was supposed to fail with an EvaluateException, but didn't")
    } catch {
      case e: EvaluateException =>
        val unwrapped = unwrapCause(e)
        assertion(message, unwrapped.getMessage)
    }
  }

  @tailrec
  private def unwrapCause(throwable: Throwable): Throwable = throwable.getCause match {
    case null => throwable
    case cause: Throwable => unwrapCause(cause)
  }

  private def assertStartsWith(expected: String, actual: String): Unit = {
    if (!actual.startsWith(expected)) {
      fail(s"$actual does not start with $expected")
    }
  }

  private def evaluateExpressionToString(expression: String)(implicit context: SuspendContextImpl): String = {
    val kind =
      if (expression.contains(System.lineSeparator())) CodeFragmentKind.CODE_BLOCK
      else CodeFragmentKind.EXPRESSION

    val evaluationContext = createEvaluationContext(context)
    val value = evaluate(kind, expression, context)
    getValueAsString(evaluationContext, value)
  }

  private def getValueAsString(context: EvaluationContextImpl, value: Value): String = value match {
    case _: VoidValue => "undefined"
    case arrayRef: ArrayReference =>
      arrayRef.getValues.asScala.map(getValueAsString(context, _)).mkString("[", ",", "]")
    case v => DebuggerUtils.getValueAsString(context, v)
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
