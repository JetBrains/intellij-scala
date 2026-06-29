package org.jetbrains.plugins.scala.debugger.breakpoints

import com.intellij.debugger.engine.{DebugProcess, SuspendContextImpl}
import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaPositionManager}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert.{assertTrue, fail}

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._

abstract class BreakpointsTestBase extends ScalaDebuggerTestCase {
  private val expectedLineQueue: ConcurrentLinkedQueue[(Int, String)] = new ConcurrentLinkedQueue()

  override protected def tearDown(): Unit = {
    try {
      if (!expectedLineQueue.isEmpty) {
        val remaining =
          expectedLineQueue.stream().collect(Collectors.toList[(Int, String)]).asScala.toList
        fail(s"The debugger did not stop on all expected lines. Remaining: $remaining")
      }
    } finally {
      super.tearDown()
    }
  }

  protected def breakpointsTest(className: String = getTestName(false))(linesAndMethods: (Int, String)*): Unit = {
    assertTrue("The test should stop on at least 1 breakpoint", linesAndMethods.nonEmpty)
    expectedLineQueue.addAll(linesAndMethods.asJava)

    createLocalProcess(className)
    val positionManager = positionManagerFor(getDebugProcess)

    onEveryBreakpoint { ctx =>
      assertStopMatchesQueue(positionManager, ctx)
    }
  }

  /**
   * Like [[breakpointsTest]], but the breakpoints marked with [[breakpointAfterStart]] are added only
   * after the debugger has already stopped once (at a regular [[breakpoint]]) - i.e. while the relevant
   * classes are already loaded. This reproduces breakpoints added in the middle of a debug session (SCL-25415).
   *
   * `linesAndMethods` lists only the stops expected at the after-start breakpoints; the initial stop used
   * to add them is consumed automatically and is not validated.
   */
  protected def breakpointsTestAddingAfterStart(className: String = getTestName(false))(linesAndMethods: (Int, String)*): Unit = {
    assertTrue("The test should stop on at least 1 breakpoint", linesAndMethods.nonEmpty)
    expectedLineQueue.addAll(linesAndMethods.asJava)

    createLocalProcess(className)
    val positionManager = positionManagerFor(getDebugProcess)
    val breakpointsAdded = new AtomicBoolean(false)

    onEveryBreakpoint { ctx =>
      if (breakpointsAdded.compareAndSet(false, true)) {
        // Stopped at the initial breakpoint added before the start; the program's classes are now loaded.
        // Add the remaining breakpoints and continue without validating this stop.
        addBreakpointsAfterStart(className)
        resume(ctx)
      } else {
        assertStopMatchesQueue(positionManager, ctx)
      }
    }
  }

  private def positionManagerFor(debugProcess: DebugProcess): ScalaPositionManager =
    ScalaPositionManager.instance(debugProcess).getOrElse(new ScalaPositionManager(debugProcess))

  private def assertStopMatchesQueue(positionManager: ScalaPositionManager, ctx: SuspendContextImpl): Unit = {
    val loc = ctx.getFrameProxy.location()
    val srcPos = inReadAction(positionManager.getSourcePosition(loc))
    val actualLine = srcPos.getLine
    val actualMethod = loc.method().name()
    Option(expectedLineQueue.poll()) match {
      case None =>
        fail(s"The debugger stopped on line $actualLine and method $actualMethod, but there were no more expected lines")
      case Some((line, method)) =>
        assertEquals(line, actualLine)
        assertEquals(method, actualMethod)
        resume(ctx)
    }
  }
}
