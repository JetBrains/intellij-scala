package org.jetbrains.plugins.scala.debugger.breakpoints

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaPositionManager}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert.{assertTrue, fail}

import java.util.concurrent.ConcurrentLinkedQueue
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

    val debugProcess = getDebugProcess
    val positionManager = ScalaPositionManager.instance(debugProcess).getOrElse(new ScalaPositionManager(debugProcess))

    onEveryBreakpoint { implicit ctx =>
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
}
